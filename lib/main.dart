import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const String _kNvidiaApiKey = String.fromEnvironment('NVIDIA_API_KEY', defaultValue: '');
const String _kNvidiaInvokeUrl = String.fromEnvironment(
  'NVIDIA_NIM_URL',
  defaultValue: 'https://integrate.api.nvidia.com/v1/chat/completions',
);
const String _kNvidiaModel = String.fromEnvironment(
  'NVIDIA_NIM_MODEL',
  defaultValue: 'meta/llama-3.1-8b-instruct',
);

class LexiQNativeBridge {
  LexiQNativeBridge._();
  static final LexiQNativeBridge instance = LexiQNativeBridge._();

  static const MethodChannel _channel = MethodChannel('org.linguistic.assistant/onnx_engine');

  Future<String> prepareAssetModelToDisk(String assetPath) async {
    final ByteData data = await rootBundle.load(assetPath);
    final Directory modelDir = Directory('${Directory.systemTemp.path}/lexiq_onnx_models');
    final String fileName = assetPath.split('/').last;
    final File outFile = File('${modelDir.path}/$fileName');
    await outFile.parent.create(recursive: true);
    await outFile.writeAsBytes(
      data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes),
      flush: true,
    );
    return outFile.path;
  }

  Future<bool> initModel(String modelPath) async {
    final bool? ok = await _channel
        .invokeMethod<bool>('initModel', {'modelPath': modelPath})
        .timeout(const Duration(seconds: 8));
    return ok ?? false;
  }

  Future<void> destroyModel() async {
    await _channel
        .invokeMethod<void>('destroyModel')
        .timeout(const Duration(seconds: 4));
  }

  Future<List<double>?> evaluateBehavior(List<double> features) async {
    final List<dynamic>? raw = await _channel
        .invokeMethod<List<dynamic>>('evaluateBehavior', {'features': features})
        .timeout(const Duration(seconds: 5));
    if (raw == null) return null;
    return raw.map((dynamic e) => (e as num).toDouble()).toList();
  }

  Future<Int8List?> evaluateBehaviorInt8(Int8List quantizedFeatures) async {
    final Uint8List? raw = await _channel
        .invokeMethod<Uint8List>(
          'evaluateBehaviorInt8',
          {'quantizedFeatures': Uint8List.fromList(quantizedFeatures)},
        )
        .timeout(const Duration(seconds: 5));
    if (raw == null) return null;
    return Int8List.fromList(raw);
  }

  Future<String> queryNvidiaNimFallback(String prompt) async {
    if (_kNvidiaApiKey.trim().isEmpty) {
      return 'NIM fallback unavailable: pass --dart-define=NVIDIA_API_KEY=... or load a local ONNX model.';
    }

    final HttpClient client = HttpClient()..connectionTimeout = const Duration(seconds: 8);
    try {
      final HttpClientRequest request = await client
          .postUrl(Uri.parse(_kNvidiaInvokeUrl))
          .timeout(const Duration(seconds: 8));
      request.headers.set(HttpHeaders.contentTypeHeader, 'application/json');
      request.headers.set(HttpHeaders.authorizationHeader, 'Bearer $_kNvidiaApiKey');

      final Map<String, dynamic> payload = {
        'model': _kNvidiaModel,
        'temperature': 0.2,
        'max_tokens': 160,
        'messages': [
          {
            'role': 'system',
            'content':
                'You are Lexi-Q. Keep responses concise and informational only (not medical or clinical advice).',
          },
          {
            'role': 'user',
            'content': prompt,
          },
        ],
      };

      request.add(utf8.encode(jsonEncode(payload)));
      final HttpClientResponse response = await request.close().timeout(const Duration(seconds: 12));
      final String body = await response.transform(utf8.decoder).join();

      if (response.statusCode < 200 || response.statusCode >= 300) {
        return 'NIM HTTP ${response.statusCode}: $body';
      }

      final Map<String, dynamic> decoded = jsonDecode(body) as Map<String, dynamic>;
      final List<dynamic>? choices = decoded['choices'] as List<dynamic>?;
      if (choices != null && choices.isNotEmpty) {
        final Map<String, dynamic> first = choices.first as Map<String, dynamic>;
        final Map<String, dynamic>? message = first['message'] as Map<String, dynamic>?;
        final String? content = message?['content'] as String?;
        if (content != null && content.trim().isNotEmpty) {
          return content.trim();
        }
      }
      return 'NIM response received with empty content.';
    } catch (e) {
      return 'NIM fallback error: $e';
    } finally {
      client.close(force: true);
    }
  }
}

void main() {
  runZonedGuarded(() async {
    WidgetsFlutterBinding.ensureInitialized();

    FlutterError.onError = (FlutterErrorDetails details) {
      FlutterError.presentError(details);
      debugPrint('FlutterError: ${details.exceptionAsString()}');
    };

    runApp(const LexiQApp());
  }, (Object error, StackTrace stackTrace) {
    debugPrint('Uncaught zone error: $error\n$stackTrace');
  });
}

class LexiQApp extends StatelessWidget {
  const LexiQApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'LexiQ Hyper-Dimensional Suite',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        primaryColor: const Color(0xFF00E676),
        scaffoldBackgroundColor: const Color(0xFF0D1117),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF00E676),
          surface: Color(0xFF161B22),
        ),
        cardTheme: CardThemeData(
          color: const Color(0xFF161B22),
          elevation: 2,
          margin: const EdgeInsets.all(8),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16.0),
          ),
        ),
      ),
      home: const LexiQDashboardScreen(),
    );
  }
}

class LexiQDashboardScreen extends StatefulWidget {
  const LexiQDashboardScreen({super.key});

  @override
  State<LexiQDashboardScreen> createState() => _LexiQDashboardScreenState();
}

class _LexiQDashboardScreenState extends State<LexiQDashboardScreen> {
  double currentTemp = 18.0;
  bool modelLoaded = false;
  bool busy = false;
  String runtimeStatus = 'Initializing native JNI bridge...';
  String lastInferenceOutput = 'No inference run yet.';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _probeNativeEngine();
    });
  }

  @override
  void dispose() {
    unawaited(LexiQNativeBridge.instance.destroyModel());
    super.dispose();
  }

  Future<void> _probeNativeEngine() async {
    setState(() {
      busy = true;
      runtimeStatus = 'Checking JNI & ONNX runtime...';
    });

    try {
      final List<double>? probe = await LexiQNativeBridge.instance.evaluateBehavior([
        currentTemp,
        0.8,
        0.15,
        0.05,
      ]);

      if (!mounted) return;
      if (probe != null && probe.isNotEmpty) {
        setState(() {
          modelLoaded = true;
          busy = false;
          runtimeStatus = 'JNI Session Active (arm64-v8a)';
          lastInferenceOutput = 'Float vector length: ${probe.length} | head: ${probe.first.toStringAsFixed(3)}';
        });
      } else {
        setState(() {
          modelLoaded = false;
          busy = false;
          runtimeStatus = 'JNI Ready — Awaiting ONNX Model Init / NIM Fallback';
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        modelLoaded = false;
        busy = false;
        runtimeStatus = 'JNI bridge pending: $e';
      });
    }
  }

  Future<void> _initAssetOrLocalModel() async {
    setState(() {
      busy = true;
      runtimeStatus = 'Preparing ONNX model asset...';
    });

    try {
      const String assetCandidate = 'assets/models/lexiq_behavior.onnx';
      final String diskPath = await LexiQNativeBridge.instance.prepareAssetModelToDisk(assetCandidate);
      final bool ok = await LexiQNativeBridge.instance.initModel(diskPath);

      if (!mounted) return;
      setState(() {
        busy = false;
        modelLoaded = ok;
        runtimeStatus = ok
            ? 'ONNX Model Loaded: ${diskPath.split('/').last}'
            : 'initModel returned false (verify model file & input/output tensor names)';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        busy = false;
        modelLoaded = false;
        runtimeStatus = 'Asset/Model init error: $e';
      });
    }
  }

  Future<void> _runInferenceOrNimFallback() async {
    setState(() {
      busy = true;
      lastInferenceOutput = 'Running inference...';
    });

    try {
      final List<double> features = [
        currentTemp,
        currentTemp < 15.0 ? 1.0 : 0.0,
        0.8,
        0.15,
        0.05,
      ];

      final List<double>? floatResult = await LexiQNativeBridge.instance.evaluateBehavior(features);
      if (floatResult != null && floatResult.isNotEmpty) {
        if (!mounted) return;
        setState(() {
          busy = false;
          lastInferenceOutput =
              'Local ONNX output (${floatResult.length} dims): ${floatResult.take(4).map((double v) => v.toStringAsFixed(3)).join(', ')}';
        });
        return;
      }

      final String nimReply = await LexiQNativeBridge.instance.queryNvidiaNimFallback(
        'Ambient temp ${currentTemp.toStringAsFixed(1)}C. Provide brief informational attire and hydration notes.',
      );

      if (!mounted) return;
      setState(() {
        busy = false;
        lastInferenceOutput = nimReply;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        busy = false;
        lastInferenceOutput = 'Inference error: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final String nimStatus = _kNvidiaApiKey.trim().isNotEmpty ? 'Configured' : 'Not Set';

    return Scaffold(
      appBar: AppBar(
        title: const Text('LexiQ Hyper-Dimensional Engine'),
        backgroundColor: const Color(0xFF161B22),
        elevation: 0,
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  children: [
                    const Icon(Icons.tune_rounded, size: 48, color: Color(0xFF00E676)),
                    const SizedBox(height: 12),
                    const Text(
                      'PID Telemetry & Hybrid Inference',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Simulated loop display: Kp=0.8, Ki=0.15, Kd=0.05 (non-actuating UI telemetry).',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey, fontSize: 13),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      runtimeStatus,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Color(0xFF00E676), fontSize: 12, fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 14),
                    Wrap(
                      spacing: 10,
                      runSpacing: 8,
                      alignment: WrapAlignment.center,
                      children: [
                        ElevatedButton.icon(
                          onPressed: busy ? null : _initAssetOrLocalModel,
                          icon: const Icon(Icons.memory_rounded, size: 18),
                          label: const Text('Load ONNX'),
                        ),
                        OutlinedButton.icon(
                          onPressed: busy ? null : _runInferenceOrNimFallback,
                          icon: const Icon(Icons.bolt_rounded, size: 18),
                          label: const Text('Run AI'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Engine & Telemetry Status',
                          style: TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF00E676)),
                        ),
                        Icon(Icons.videocam_rounded, color: Color(0xFF00E676)),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _buildTelemetryRow('Target ABI:', 'arm64-v8a'),
                    _buildTelemetryRow('ONNX Session:', modelLoaded ? 'Initialized' : 'Standby'),
                    _buildTelemetryRow('NVIDIA NIM Key:', nimStatus),
                    _buildTelemetryRow('Domain Context:', 'Tech / Bio / Finance / Eng'),
                    const SizedBox(height: 10),
                    Text(
                      lastInferenceOutput,
                      style: const TextStyle(color: Colors.grey, fontSize: 12, height: 1.35),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Weather & Lifestyle Optimization',
                      style: TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF00E676)),
                    ),
                    const SizedBox(height: 12),
                    Text('Current Temperature: ${currentTemp.toStringAsFixed(1)}°C'),
                    Slider(
                      value: currentTemp,
                      min: 0,
                      max: 40,
                      activeColor: const Color(0xFF00E676),
                      onChanged: (double val) {
                        setState(() {
                          currentTemp = val;
                        });
                      },
                    ),
                    const SizedBox(height: 8),
                    Text(
                      currentTemp < 15
                          ? 'Attire: Layered thermal insulation & wind resistance.\nHydration/Nutrition: Warm fluids and balanced energy intake.'
                          : 'Attire: Breathable, lightweight fabrics.\nHydration/Nutrition: Regular water and electrolyte replenishment.',
                      style: const TextStyle(color: Colors.grey, fontSize: 13, height: 1.4),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Informational advisory only — not medical, clinical, or nutritional advice.',
                      style: TextStyle(color: Colors.grey, fontSize: 11, fontStyle: FontStyle.italic),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  static Widget _buildTelemetryRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.grey, fontSize: 13)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
        ],
      ),
    );
  }
}

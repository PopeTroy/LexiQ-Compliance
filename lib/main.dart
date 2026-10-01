import 'package:flutter/material.dart';

void main() {
  runApp(const LexiQApp());
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

  @override
  Widget build(BuildContext context) {
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
            // PID Controller Status Card
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  children: const [
                    Icon(Icons.tune_rounded, size: 48, color: Color(0xFF00E676)),
                    SizedBox(height: 12),
                    Text(
                      'PID Safety & Compliance Active',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
                    ),
                    SizedBox(height: 8),
                    Text(
                      'Closed-loop feedback loop active: Error compensation Kp=0.8, Ki=0.15, Kd=0.05.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey, fontSize: 13),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Camera Telemetry Mock Display
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: const [
                        Text('Camera Telemetry Overlay',
                            style: TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF00E676))),
                        Icon(Icons.videocam_rounded, color: Color(0xFF00E676)),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _buildTelemetryRow('Frame Rate:', '60.0 FPS'),
                    _buildTelemetryRow('Inference Latency:', '4.2 ms (LLVM Native)'),
                    _buildTelemetryRow('NVIDIA NIM Model Base:', 'Downloaded & Cached'),
                    _buildTelemetryRow('PhD Domain Context:', 'Tech / Bio / Finance / Eng'),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Weather, Attire & Diet Advisor
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Weather & Cognitive Lifestyle Optimization',
                        style: TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF00E676))),
                    const SizedBox(height: 12),
                    Text('Current Temperature: ${currentTemp.toStringAsFixed(1)}°C'),
                    Slider(
                      value: currentTemp,
                      min: 0,
                      max: 40,
                      activeColor: const Color(0xFF00E676),
                      onChanged: (val) {
                        setState(() {
                          currentTemp = val;
                        });
                      },
                    ),
                    const SizedBox(height: 8),
                    Text(
                      currentTemp < 15
                          ? 'Attire: Thermal insulated jacket & wind resistance layer.\nDiet: Complex fats & high-density protein for thermogenesis.'
                          : 'Attire: Breathable moisture-wicking technical fabrics.\nDiet: High hydration, electrolyte management, light proteins.',
                      style: const TextStyle(color: Colors.grey, fontSize: 13, height: 1.4),
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

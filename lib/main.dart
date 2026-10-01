import 'package:flutter/material.dart';

void main() {
  runApp(const LexiQApp());
}

class LexiQApp extends StatelessWidget {
  const LexiQApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'LexiQ Grammar Suite',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        primaryColor: const Color(0xFF00E676),
        scaffoldBackgroundColor: const Color(0xFF121212),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF00E676),
          surface: Color(0xFF1E1E1E),
        ),
        // Updated from CardTheme to CardThemeData for Flutter 3.x compatibility
        cardTheme: CardThemeData(
          color: const Color(0xFF1E1E1E),
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

class LexiQDashboardScreen extends StatelessWidget {
  const LexiQDashboardScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('LexiQ Grammar Suite (Free Edition)'),
        backgroundColor: const Color(0xFF1E1E1E),
        elevation: 0,
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Status Card
            Card(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  children: const [
                    Icon(
                      Icons.keyboard_arrow_up_rounded,
                      size: 64,
                      color: Color(0xFF00E676),
                    ),
                    SizedBox(height: 12),
                    Text(
                      'LexiQ Hybrid Engine Active',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 20,
                        letterSpacing: 0.3,
                      ),
                    ),
                    SizedBox(height: 8),
                    Text(
                      'Offline ONNX model loaded for real-time physics-aware spellcheck & grammar.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey, height: 1.4),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),

            // Dual Engine Status
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Engine Architecture',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                        color: Color(0xFF00E676),
                      ),
                    ),
                    const SizedBox(height: 16),
                    _buildFeatureTile(
                      icon: Icons.offline_bolt_rounded,
                      title: 'Primary: Offline ONNX Model (onnx.cql)',
                      subtitle: 'Zero-latency local processing. Runs on-device with quantized execution.',
                    ),
                    const Divider(height: 24, color: Colors.white10),
                    _buildFeatureTile(
                      icon: Icons.cloud_done_rounded,
                      title: 'Fallback: NVIDIA NIM Microservices',
                      subtitle: 'Online LLM enhancement for complex multi-modal & vision tasks.',
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),

            // Active Status Pill
            Container(
              padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
              decoration: BoxDecoration(
                color: const Color(0xFF1E1E1E),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFF00E676).withOpacity(0.3)),
              ),
              child: Row(
                children: const [
                  Icon(Icons.check_circle, color: Color(0xFF00E676)),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'onnx.cql model pre-loaded in android/app/src/main/assets/',
                      style: TextStyle(fontSize: 13, color: Colors.white70),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  static Widget _buildFeatureTile({
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        CircleAvatar(
          backgroundColor: const Color(0xFF00E676).withOpacity(0.12),
          child: Icon(icon, color: const Color(0xFF00E676)),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
              ),
              const SizedBox(height: 4),
              Text(
                subtitle,
                style: const TextStyle(color: Colors.grey, fontSize: 13, height: 1.3),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

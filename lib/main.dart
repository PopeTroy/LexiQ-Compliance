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
      theme: ThemeData(
        brightness: Brightness.dark,
        primaryColor: const Color(0xFF00E676),
        scaffoldBackgroundColor: const Color(0xFF121212),
      ),
      home: const Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.keyboard_arrow_up, size: 64, color: Color(0xFF00E676)),
              SizedBox(height: 16),
              Text(
                'LexiQ Keyboard Extension Installed',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
              ),
              SizedBox(height: 8),
              Text(
                'Activate via Android Settings > Languages & Input',
                style: TextStyle(color: Colors.grey),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

import 'dart:ffi' as ffi;
import 'dart:io' show Platform;
import 'package:flutter/material.dart';

void main() {
  runApp(const LexiQApp());
}

class LexiQApp extends StatelessWidget {
  const LexiQApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'LexiQ Compliance',
      theme: ThemeData(
        brightness: Brightness.dark,
        primaryColor: const Color(0xFF00E676), // Sleek Compliance Green
        scaffoldBackgroundColor: const Color(0xFF121212),
      ),
      home: const ComplianceDashboard(),
    );
  }
}

class ComplianceDashboard extends StatefulWidget {
  const ComplianceDashboard({super.key});

  @override
  State<ComplianceDashboard> createState() => _ComplianceDashboardState();
}

class _ComplianceDashboardState extends State<ComplianceDashboard> {
  String _selectedTier = 'T1 (Local Core)';
  String _sessionLog = 'No active session. Tap "Initialize Compliance" to begin.';
  bool _isProcessing = false;

  // Simple FFI hook to verify the native .so compiled correctly in the APK
  void _testNativeBinding() {
    try {
      if (Platform.isAndroid) {
        // This dynamically links to the compiled liblexiq_compliance.so in jniLibs
        final ffi.DynamicLibrary nativeLib = ffi.DynamicLibrary.open('liblexiq_compliance.so');
        setState(() {
          _sessionLog = "Native binary loaded successfully!\nLibrary Path: $nativeLib";
        });
      } else {
        setState(() {
          _sessionLog = "Native loading bypassed. Please run on an Android device/emulator.";
        });
      }
    } catch (e) {
      setState(() {
        _sessionLog = "Error loading native library: $e\nEnsure JNI compilation step is complete.";
      });
    }
  }

  void _runComplianceAudit() {
    setState(() {
      _isProcessing = true;
      _sessionLog = "Initializing 80 Ephemeral Sentinel Clones...\n";
    });

    // Simulate the non-linear execution flow using the selected tier suffix
    Future.delayed(const Duration(milliseconds: 800), () {
      final String suffix = _selectedTier.split(' ')[0];
      final String sessionId = "ZAR-SESS-910283-$suffix";
      
      setState(() {
        _isProcessing = false;
        _sessionLog = """
=== SECURE TRANSACTION INITIALIZED ===
Session ID: $sessionId
Temporal Sync: 1:6000 SAST Quantum Clock
Compliance Status: SECURE / VERIFIED BY AUDITOR BASE

[Audit Frame Status]
• Active Level: $suffix
• Sanitization Layer: ACTIVE (POPIA Compliant)
• Swarm State: 80 Sentinel Clones dispatched and dropped.
""";
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('🛡️ LexiQ Compliance Engine'),
        backgroundColor: const Color(0xFF1E1E1E),
        elevation: 4,
      ),
      body: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Card(
              color: Color(0xFF1E1E1E),
              child: Padding(
                padding: EdgeInsets.all(16.0),
                child: Column(
                  children: [
                    Text(
                      'SOUTHERN AFRICA REGULATORY PORTAL',
                      style: TextStyle(
                        color: Color(0xFF00E676),
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1.2,
                      ),
                    ),
                    SizedBox(height: 8),
                    Text(
                      'POPIA Section 19 & ECT Act Non-Repudiation Architecture',
                      style: TextStyle(color: Colors.grey, fontSize: 12),
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            const Text(
              'Select Execution Tier Level:',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              decoration: BoxDecoration(
                color: const Color(0xFF1E1E1E),
                borderRadius: BorderRadius.circular(8),
              ),
              child: DropdownButtonHideUnderline(
                child: DropdownButton<String>(
                  value: _selectedTier,
                  dropdownColor: const Color(0xFF1E1E1E),
                  style: const TextStyle(color: Colors.white, fontSize: 16),
                  items: <String>['T1 (Local Core)', 'T2 (Pro Sync)', 'T3 (Enterprise)']
                      .map<DropdownMenuItem<String>>((String value) {
                    return DropdownMenuItem<String>(
                      value: value,
                      child: Text(value),
                    );
                  }).toList(),
                  onChanged: (String? newValue) {
                    if (newValue != null) {
                      setState(() {
                        _selectedTier = newValue;
                      });
                    }
                  },
                ),
              ),
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: _isProcessing ? null : _runComplianceAudit,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF00E676),
                      foregroundColor: Colors.black,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                    ),
                    child: _isProcessing
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(color: Colors.black, strokeWidth: 2),
                          )
                        : const Text('Initialize Compliance', style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                ),
                const SizedBox(width: 10),
                IconButton(
                  onPressed: _testNativeBinding,
                  icon: const Icon(Icons.bolt, color: Colors.amber),
                  tooltip: 'Test Rust JNI Binding',
                  style: IconButton.styleFrom(
                    backgroundColor: const Color(0xFF1E1E1E),
                    padding: const EdgeInsets.all(16),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 25),
            const Text(
              'Auditor Transaction Output:',
              style: TextStyle(fontSize: 14, color: Colors.grey),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: const Color(0xFF0D0D0D),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0xFF1E1E1E)),
                ),
                child: SingleChildScrollView(
                  child: Text(
                    _sessionLog,
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 13,
                      color: Color(0xFFE0E0E0),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

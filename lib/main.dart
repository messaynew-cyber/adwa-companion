import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'services/api_service.dart';
import 'services/notification_service.dart';
import 'overlay/overlay_widget.dart';
import 'screens/settings_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
  runApp(const AdwaCompanionApp());
}

class AdwaCompanionApp extends StatefulWidget {
  const AdwaCompanionApp({super.key});

  @override
  State<AdwaCompanionApp> createState() => _AdwaCompanionAppState();
}

class _AdwaCompanionAppState extends State<AdwaCompanionApp> {
  final _api = ApiService();

  @override
  void initState() {
    super.initState();
    _api.startPolling();
    NotificationService.showPersistent();
  }

  @override
  void dispose() {
    _api.dispose();
    NotificationService.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ADWA',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF07070d),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF00c853),
          secondary: Color(0xFF00c853),
        ),
      ),
      home: _MainScreen(api: _api),
    );
  }
}

class _MainScreen extends StatefulWidget {
  final ApiService api;
  const _MainScreen({required this.api});

  @override
  State<_MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<_MainScreen> {
  bool _overlayActive = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF07070d),
      appBar: AppBar(
        title: const Row(
          children: [
            Text('👁', style: TextStyle(fontSize: 20)),
            SizedBox(width: 8),
            Text('ADWA', style: TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.w700, letterSpacing: 2)),
          ],
        ),
        backgroundColor: const Color(0xFF12121a),
        foregroundColor: Colors.white,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.settings, color: Colors.white54),
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => SettingsScreen(api: widget.api))),
          ),
        ],
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Floating eye preview
              Container(
                width: 100,
                height: 100,
                decoration: BoxDecoration(
                  color: const Color(0xFF07070d),
                  shape: BoxShape.circle,
                  boxShadow: [BoxShadow(color: Colors.green.withOpacity(0.2), blurRadius: 20)],
                ),
                child: const Icon(Icons.visibility, color: Colors.greenAccent, size: 48),
              ),
              const SizedBox(height: 40),
              Text(
                _overlayActive ? 'OVERLAY ACTIVE' : 'TAP TO START',
                style: TextStyle(
                  color: _overlayActive ? Colors.greenAccent : Colors.white38,
                  fontSize: 13,
                  fontFamily: 'monospace',
                  letterSpacing: 3,
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _overlayActive ? Colors.red.withOpacity(0.15) : Colors.green.withOpacity(0.15),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                      side: BorderSide(color: _overlayActive ? Colors.red.withOpacity(0.3) : Colors.green.withOpacity(0.3)),
                    ),
                  ),
                  onPressed: _toggleOverlay,
                  child: Text(
                    _overlayActive ? 'STOP OVERLAY' : 'START OVERLAY',
                    style: TextStyle(
                      color: _overlayActive ? Colors.redAccent : Colors.greenAccent,
                      fontFamily: 'monospace',
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                      letterSpacing: 2,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'Double-tap the eye to open Telegram',
                style: TextStyle(color: Colors.white24, fontSize: 11, fontFamily: 'monospace'),
              ),
              Text(
                'Drag to reposition  •  Tap for data',
                style: TextStyle(color: Colors.white24, fontSize: 11, fontFamily: 'monospace'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _toggleOverlay() {
    setState(() => _overlayActive = !_overlayActive);
    if (_overlayActive) {
      _showOverlay();
    }
  }

  void _showOverlay() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          backgroundColor: Colors.transparent,
          body: Stack(
            children: [
              GestureDetector(
                onTap: () {
                  Navigator.of(context).pop();
                  setState(() => _overlayActive = false);
                },
                child: Container(color: Colors.black12),
              ),
              AdwaOverlay(api: widget.api),
            ],
          ),
        ),
        fullscreenDialog: true,
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'services/api_service.dart';
import 'services/notification_service.dart';
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
  static const _channel = MethodChannel('com.adwa.companion/overlay');
  bool _overlayActive = false;
  bool _hasPermission = false;

  @override
  void initState() {
    super.initState();
    _api.startPolling();
    NotificationService.showPersistent();
    _checkState();
  }

  Future<void> _checkState() async {
    try {
      final hasPerm = await _channel.invokeMethod<bool>('checkPermission');
      final running = await _channel.invokeMethod<bool>('isRunning');
      setState(() {
        _hasPermission = hasPerm ?? false;
        _overlayActive = running ?? false;
      });
    } catch (_) {}
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
      home: _MainScreen(
        api: _api,
        overlayActive: _overlayActive,
        hasPermission: _hasPermission,
        onToggle: _toggleOverlay,
        onDismiss: _dismissApp,
        onRefresh: _checkState,
      ),
    );
  }

  Future<void> _toggleOverlay() async {
    if (_overlayActive) {
      await _channel.invokeMethod('stopOverlay');
      setState(() => _overlayActive = false);
    } else {
      if (!_hasPermission) {
        await _channel.invokeMethod('requestPermission');
        // Permission screen opened — user must grant manually then return
        setState(() => _hasPermission = true);
        return;
      }
      final ok = await _channel.invokeMethod<bool>('startOverlay');
      if (ok == true) {
        setState(() => _overlayActive = true);
      } else {
        // Permission might have been revoked or overlay failed
        setState(() => _hasPermission = false);
      }
    }
  }

  Future<void> _dismissApp() async {
    await _channel.invokeMethod('dismissApp');
  }
}

class _MainScreen extends StatefulWidget {
  final ApiService api;
  final bool overlayActive;
  final bool hasPermission;
  final VoidCallback onToggle;
  final VoidCallback onDismiss;
  final VoidCallback onRefresh;

  const _MainScreen({
    required this.api,
    required this.overlayActive,
    required this.hasPermission,
    required this.onToggle,
    required this.onDismiss,
    required this.onRefresh,
  });

  @override
  State<_MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<_MainScreen> {
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
            icon: const Icon(Icons.refresh, color: Colors.white54, size: 20),
            onPressed: widget.onRefresh,
          ),
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
              // Eye preview (decorative)
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

              if (!widget.hasPermission && !widget.overlayActive) ...[
                const Text(
                  'PERMISSION REQUIRED',
                  style: TextStyle(color: Colors.amber, fontSize: 12, fontFamily: 'monospace', letterSpacing: 2),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Android requires "Display over other apps"\npermission for the floating eye.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white38, fontSize: 11, fontFamily: 'monospace'),
                ),
                const SizedBox(height: 24),
              ],

              Text(
                widget.overlayActive ? 'OVERLAY ACTIVE' : 'TAP TO START',
                style: TextStyle(
                  color: widget.overlayActive ? Colors.greenAccent : Colors.white38,
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
                    backgroundColor: widget.overlayActive
                        ? Colors.red.withOpacity(0.15)
                        : Colors.green.withOpacity(0.15),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                      side: BorderSide(
                        color: widget.overlayActive
                            ? Colors.red.withOpacity(0.3)
                            : Colors.green.withOpacity(0.3),
                      ),
                    ),
                  ),
                  onPressed: widget.onToggle,
                  child: Text(
                    widget.overlayActive ? 'STOP OVERLAY' : 'START OVERLAY',
                    style: TextStyle(
                      color: widget.overlayActive ? Colors.redAccent : Colors.greenAccent,
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
                'Tap for dashboard  •  Double-tap → Telegram bot',
                style: TextStyle(color: Colors.white24, fontSize: 11, fontFamily: 'monospace'),
              ),
              Text(
                'Drag to move  •  ✕ appears to close overlay',
                style: TextStyle(color: Colors.white24, fontSize: 11, fontFamily: 'monospace'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/api_service.dart';

class SettingsScreen extends StatefulWidget {
  final ApiService api;
  const SettingsScreen({super.key, required this.api});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _overlayEnabled = true;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF07070d),
      appBar: AppBar(
        title: const Text('ADWA Settings', style: TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.w600)),
        backgroundColor: const Color(0xFF12121a),
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          // Overlay toggle
          _section('OVERLAY'),
          const SizedBox(height: 8),
          SwitchListTile(
            title: const Text('Floating Eye', style: TextStyle(color: Colors.white, fontFamily: 'monospace')),
            subtitle: const Text('Show ADWA on top of other apps', style: TextStyle(color: Colors.white38, fontSize: 12)),
            value: _overlayEnabled,
            activeColor: Colors.greenAccent,
            onChanged: (v) {
              setState(() => _overlayEnabled = v);
              HapticFeedback.lightImpact();
            },
          ),
          const Divider(color: Colors.white12, height: 32),

          // Telegram
          _section('QUICK ACTIONS'),
          const SizedBox(height: 8),
          _actionTile(Icons.telegram, 'Open Telegram', 'Double-tap the eye to open @AdwaAuditor'),
          const Divider(color: Colors.white12, height: 32),

          // About
          _section('ABOUT'),
          const SizedBox(height: 8),
          _infoTile('Version', '1.0.0'),
          _infoTile('Data endpoint', '129.80.112.9/adwa-status.json'),
          _infoTile('Refresh rate', 'Every 30s'),
        ],
      ),
    );
  }

  Widget _section(String label) {
    return Text(label, style: const TextStyle(color: Colors.greenAccent, fontSize: 11, letterSpacing: 1.5, fontFamily: 'monospace'));
  }

  Widget _actionTile(IconData icon, String title, String subtitle) {
    return ListTile(
      leading: Icon(icon, color: Colors.greenAccent, size: 20),
      title: Text(title, style: const TextStyle(color: Colors.white, fontFamily: 'monospace', fontSize: 14)),
      subtitle: Text(subtitle, style: const TextStyle(color: Colors.white38, fontSize: 11)),
    );
  }

  Widget _infoTile(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.white54, fontSize: 12, fontFamily: 'monospace')),
          Text(value, style: const TextStyle(color: Colors.white38, fontSize: 12, fontFamily: 'monospace')),
        ],
      ),
    );
  }
}

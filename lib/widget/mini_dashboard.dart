import 'dart:async';
import 'package:flutter/material.dart';
import '../models/status_data.dart';
import '../services/api_service.dart';

class MiniDashboard extends StatefulWidget {
  final ApiService api;
  const MiniDashboard({super.key, required this.api});

  @override
  State<MiniDashboard> createState() => _MiniDashboardState();
}

class _MiniDashboardState extends State<MiniDashboard> {
  AdwaStatus _status = AdwaStatus.empty();
  StreamSubscription? _sub;

  @override
  void initState() {
    super.initState();
    _status = widget.api.lastStatus;
    _sub = widget.api.stream.listen((s) => setState(() => _status = s));
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Container(
        width: 160,
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: const Color(0xDD07070d),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: Colors.white12),
          boxShadow: [BoxShadow(color: Colors.black45, blurRadius: 12)],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            _row('SOL', '\$${_status.solPrice.toStringAsFixed(2)}', _status.isUp ? Colors.greenAccent : _status.isDown ? Colors.redAccent : Colors.amberAccent),
            _row('Δ', '${_status.solChange.toStringAsFixed(2)}%', _status.isUp ? Colors.greenAccent : Colors.redAccent),
            const Divider(height: 8, color: Colors.white12),
            _row('Equity', '\$${_status.equity.toStringAsFixed(2)}', Colors.greenAccent),
            _row('Batt', '${_status.battery}%', _status.battery > 20 ? Colors.greenAccent : Colors.redAccent),
            const SizedBox(height: 4),
            Text('🔋 ${_status.batteryStatus}', style: const TextStyle(color: Colors.white38, fontSize: 9, fontFamily: 'monospace')),
            Text('🐺 ${_status.swarmOnline} online', style: const TextStyle(color: Colors.white38, fontSize: 9, fontFamily: 'monospace')),
          ],
        ),
      ),
    );
  }

  Widget _row(String label, String value, Color color) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.white54, fontSize: 10, fontFamily: 'monospace')),
          Text(value, style: TextStyle(color: color, fontSize: 11, fontFamily: 'monospace', fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

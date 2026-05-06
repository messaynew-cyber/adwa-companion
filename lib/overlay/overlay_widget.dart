import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/status_data.dart';
import '../services/api_service.dart';
import 'eye_painter.dart';
import '../widget/mini_dashboard.dart';

class AdwaOverlay extends StatefulWidget {
  final ApiService api;
  const AdwaOverlay({super.key, required this.api});

  @override
  State<AdwaOverlay> createState() => _AdwaOverlayState();
}

class _AdwaOverlayState extends State<AdwaOverlay> with SingleTickerProviderStateMixin {
  late AnimationController _animCtrl;
  double _blink = 0.0;
  double _pupilOffset = 0.0;
  Offset _position = const Offset(50, 200);
  Offset _target = const Offset(50, 200);
  bool _showTooltip = false;
  bool _moving = false;
  Color _glowColor = Colors.green;
  Timer? _moveTimer;
  Timer? _blinkTimer;
  StreamSubscription? _sub;

  @override
  void initState() {
    super.initState();
    _animCtrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 600));
    _animCtrl.addListener(() => setState(() {}));

    // Breath animation
    _animCtrl.repeat(reverse: true);

    // Blink every 3-6 seconds
    _blinkTimer = Timer.periodic(const Duration(seconds: 4), (_) => _doBlink());

    // Subscribe to data
    _sub = widget.api.stream.listen(_onData);

    // Start wandering
    _scheduleMove();
  }

  void _onData(AdwaStatus status) {
    setState(() {
      _glowColor = status.isUp ? Colors.green : status.isDown ? Colors.red : Colors.amber;
    });
  }

  void _doBlink() {
    setState(() => _blink = 0.0);
    Future.delayed(const Duration(milliseconds: 50), () {
      setState(() => _blink = 1.0);
      Future.delayed(const Duration(milliseconds: 120), () {
        setState(() => _blink = 0.0);
      });
    });
  }

  void _scheduleMove() {
    _moveTimer?.cancel();
    final delay = Duration(seconds: 5 + Random().nextInt(10));
    _moveTimer = Timer(delay, () {
      final screen = MediaQuery.of(context).size;
      final padding = 60.0;
      setState(() {
        _target = Offset(
          padding + Random().nextDouble() * (screen.width - padding * 2),
          padding + Random().nextDouble() * (screen.height - padding * 2),
        );
        _moving = true;
      });
      // Animate position
      Future.doWhile(() async {
        await Future.delayed(const Duration(milliseconds: 16));
        if (!mounted) return false;
        final diff = _target - _position;
        final dist = diff.distance;
        if (dist < 2) {
          setState(() => _moving = false);
          // Random pupil dart
          setState(() => _pupilOffset = Random().nextDouble() * 2 - 1);
          return false;
        }
        setState(() {
          _position += diff * 0.02;
          _pupilOffset = sin(DateTime.now().millisecondsSinceEpoch / 300) * 0.3;
        });
        return true;
      });
      _scheduleMove();
    });
  }

  void _onTap() {
    setState(() => _showTooltip = !_showTooltip);
    _doBlink();
  }

  void _onDoubleTap() {
    HapticFeedback.heavyImpact();
    _doBlink();
    // Open Telegram
    // This would use url_launcher in production
    debugPrint('Open Telegram: tg://resolve?domain=AdwaAuditor');
  }

  @override
  void dispose() {
    _animCtrl.dispose();
    _blinkTimer?.cancel();
    _moveTimer?.cancel();
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final breath = 1.0 + 0.03 * sin(_animCtrl.value * pi * 2);
    return Stack(
      children: [
        // Eye bubble
        Positioned(
          left: _position.dx,
          top: _position.dy,
          child: GestureDetector(
            onTap: _onTap,
            onDoubleTap: _onDoubleTap,
            onPanUpdate: (d) {
              setState(() {
                _position += d.delta;
                _target = _position;
                _moving = false;
              });
            },
            child: Transform.scale(
              scale: breath,
              child: Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(
                  color: const Color(0xFF07070d),
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(color: _glowColor.withOpacity(0.3), blurRadius: 12, spreadRadius: 2),
                  ],
                ),
                child: CustomPaint(
                  painter: EyePainter(
                    blinkAmount: _blink,
                    glowColor: _glowColor,
                    pupilOffset: _pupilOffset,
                  ),
                ),
              ),
            ),
          ),
        ),
        // Tooltip
        if (_showTooltip)
          Positioned(
            left: _position.dx - 80,
            top: _position.dy + 64,
            child: MiniDashboard(api: widget.api),
          ),
      ],
    );
  }
}

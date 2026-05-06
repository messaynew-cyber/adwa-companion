import 'package:flutter/material.dart';
import 'dart:math';

class EyePainter extends CustomPainter {
  final double blinkAmount;  // 0.0 = open, 1.0 = closed
  final Color glowColor;
  final double pupilOffset;  // slight movement for liveliness

  EyePainter({
    this.blinkAmount = 0.0,
    this.glowColor = Colors.green,
    this.pupilOffset = 0.0,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final cx = size.width / 2;
    final cy = size.height / 2;
    final rx = size.width * 0.38;
    final ry = size.height * 0.35 * (1.0 - blinkAmount * 0.7);
    final glowPaint = Paint()
      ..color = glowColor.withOpacity(0.15 + blinkAmount * 0.1)
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 12);
    canvas.drawOval(Rect.fromCenter(center: Offset(cx, cy), width: rx * 2.5, height: ry * 2.5), glowPaint);

    // Sclera (white)
    final sclera = Paint()..color = Colors.white.withOpacity(0.95);
    canvas.drawOval(Rect.fromCenter(center: Offset(cx, cy), width: rx * 2, height: ry * 2), sclera);

    // Iris
    final iris = Paint()..color = const Color(0xFF1a1a2e);
    final irisRx = rx * 0.65;
    final irisRy = ry * 0.65 * (1.0 - blinkAmount * 0.5);
    final pupilX = cx + pupilOffset * rx * 0.3;
    canvas.drawOval(Rect.fromCenter(center: Offset(pupilX, cy), width: irisRx * 2, height: irisRy * 2), iris);

    // Pupil
    final pupilPaint = Paint()..color = const Color(0xFF000000);
    final pSize = rx * 0.28 * (1.0 - blinkAmount * 0.3);
    canvas.drawCircle(Offset(pupilX, cy), pSize, pupilPaint);

    // Highlight
    final hl = Paint()..color = Colors.white.withOpacity(0.8);
    canvas.drawCircle(Offset(pupilX + rx * 0.15, cy - ry * 0.2), rx * 0.12, hl);
    canvas.drawCircle(Offset(pupilX + rx * 0.08, cy - ry * 0.25), rx * 0.06, hl);

    // Lid (for blink)
    if (blinkAmount > 0.01) {
      final lid = Paint()..color = const Color(0xFF07070d);
      final lidH = size.height * 0.5 * blinkAmount;
      canvas.drawRect(Rect.fromLTWH(0, cy - lidH - ry, size.width, lidH + ry), lid);
      canvas.drawRect(Rect.fromLTWH(0, cy + ry - lidH * 0.5, size.width, lidH + ry), lid);
    }
  }

  @override
  bool shouldRepaint(EyePainter old) => true;
}

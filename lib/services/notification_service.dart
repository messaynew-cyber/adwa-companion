import 'package:flutter_local_notifications/flutter_local_notifications.dart';

class NotificationService {
  static final _plugin = FlutterLocalNotificationsPlugin();
  static bool _initialized = false;

  static Future<void> init() async {
    if (_initialized) return;
    const android = AndroidInitializationSettings('@mipmap/ic_launcher');
    await _plugin.initialize(const InitializationSettings(android: android));
    _initialized = true;
  }

  static Future<void> showPersistent() async {
    await init();
    const details = AndroidNotificationDetails(
      'adwa_overlay',
      'ADWA Companion',
      channelDescription: 'Keeps the overlay service alive',
      importance: Importance.low,
      priority: Priority.low,
      ongoing: true,
      showWhen: false,
    );
    await _plugin.show(
      1,
      'ADWA',
      'Overlay active — tap to open',
      NotificationDetails(android: details),
    );
  }

  static Future<void> cancel() async {
    await _plugin.cancel(1);
  }
}

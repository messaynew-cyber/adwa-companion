import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/status_data.dart';

class ApiService {
  static const _url = 'http://129.80.112.9/adwa-status.json';
  Timer? _timer;
  AdwaStatus _lastStatus = AdwaStatus.empty();
  final _controller = StreamController<AdwaStatus>.broadcast();

  Stream<AdwaStatus> get stream => _controller.stream;
  AdwaStatus get lastStatus => _lastStatus;

  void startPolling({Duration interval = const Duration(seconds: 30)}) {
    _fetch();
    _timer = Timer.periodic(interval, (_) => _fetch());
  }

  void stopPolling() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> _fetch() async {
    try {
      final resp = await http.get(Uri.parse(_url)).timeout(const Duration(seconds: 5));
      if (resp.statusCode == 200) {
        final json = jsonDecode(resp.body);
        _lastStatus = AdwaStatus.fromJson(json);
        _controller.add(_lastStatus);
      }
    } catch (_) {}
  }

  void dispose() {
    stopPolling();
    _controller.close();
  }
}

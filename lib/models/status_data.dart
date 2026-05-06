class AdwaStatus {
  final double solPrice;
  final double solChange;
  final double equity;
  final double cash;
  final int battery;
  final String batteryStatus;
  final double temperature;
  final String swarmOnline;
  final String direction;

  AdwaStatus({
    required this.solPrice,
    required this.solChange,
    required this.equity,
    required this.cash,
    required this.battery,
    required this.batteryStatus,
    required this.temperature,
    required this.swarmOnline,
    required this.direction,
  });

  factory AdwaStatus.fromJson(Map<String, dynamic> json) {
    final mkts = json['markets'] ?? {};
    final pf = json['portfolio'] ?? {};
    final sys = json['system'] ?? {};
    final sw = json['swarm'] ?? {};
    final sol = mkts['sol'] ?? {};

    return AdwaStatus(
      solPrice: (sol['price'] ?? 0).toDouble(),
      solChange: (sol['change_24h'] ?? 0).toDouble(),
      equity: (pf['equity'] ?? 0).toDouble(),
      cash: (pf['cash'] ?? 0).toDouble(),
      battery: sys['battery'] ?? 0,
      batteryStatus: sys['battery_status'] ?? 'unknown',
      temperature: (sys['temperature'] ?? 0).toDouble(),
      swarmOnline: sw['online'] ?? '?',
      direction: sol['direction'] ?? 'flat',
    );
  }

  factory AdwaStatus.empty() => AdwaStatus(
    solPrice: 0, solChange: 0, equity: 0, cash: 0,
    battery: 0, batteryStatus: 'unknown', temperature: 0,
    swarmOnline: '?', direction: 'flat',
  );

  bool get isUp => direction == 'up';
  bool get isDown => direction == 'down';
}

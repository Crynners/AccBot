import React from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { useAccBotStore } from '../src/store/accbotStore';

export default function StatsScreen() {
  const { stats, refreshStats, isLoading } = useAccBotStore();
  const [refreshing, setRefreshing] = React.useState(false);

  const onRefresh = React.useCallback(async () => {
    setRefreshing(true);
    await refreshStats();
    setRefreshing(false);
  }, [refreshStats]);

  if (!stats) {
    return (
      <View style={styles.container}>
        <View style={styles.emptyState}>
          <Text style={styles.emptyEmoji}>📊</Text>
          <Text style={styles.emptyTitle}>No Statistics Yet</Text>
          <Text style={styles.emptyText}>
            Statistics will appear after your bot makes its first purchase.
          </Text>
        </View>
      </View>
    );
  }

  const avgInvestment = stats.totalBuys > 0 ? stats.totalInvested / stats.totalBuys : 0;
  const avgCrypto = stats.totalBuys > 0 ? stats.totalCrypto / stats.totalBuys : 0;

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={onRefresh}
          tintColor="#4ecca3"
        />
      }
    >
      {/* Main Stats */}
      <View style={styles.mainCard}>
        <Text style={styles.mainTitle}>Total Accumulated</Text>
        <Text style={styles.mainValue}>{stats.totalCrypto.toFixed(8)}</Text>
        <Text style={styles.mainSubtitle}>{stats.currency}</Text>
      </View>

      {/* Stats Grid */}
      <View style={styles.statsRow}>
        <View style={[styles.statCard, styles.statCardHalf]}>
          <Text style={styles.statLabel}>Total Invested</Text>
          <Text style={styles.statValue}>{stats.totalInvested.toFixed(2)}</Text>
          <Text style={styles.statUnit}>{stats.fiat}</Text>
        </View>
        <View style={[styles.statCard, styles.statCardHalf]}>
          <Text style={styles.statLabel}>Avg Buy Price</Text>
          <Text style={styles.statValue}>{stats.avgPrice.toFixed(0)}</Text>
          <Text style={styles.statUnit}>{stats.fiat}</Text>
        </View>
      </View>

      <View style={styles.statsRow}>
        <View style={[styles.statCard, styles.statCardHalf]}>
          <Text style={styles.statLabel}>Total Buys</Text>
          <Text style={styles.statValue}>{stats.totalBuys}</Text>
          <Text style={styles.statUnit}>transactions</Text>
        </View>
        <View style={[styles.statCard, styles.statCardHalf]}>
          <Text style={styles.statLabel}>Current Price</Text>
          <Text style={styles.statValue}>{stats.currentPrice.toFixed(0)}</Text>
          <Text style={styles.statUnit}>{stats.fiat}</Text>
        </View>
      </View>

      {/* P/L Section */}
      <View style={[styles.plCard, stats.profitPercent >= 0 ? styles.plPositive : styles.plNegative]}>
        <View style={styles.plRow}>
          <View style={styles.plItem}>
            <Text style={styles.plLabel}>Profit/Loss</Text>
            <Text style={[styles.plValue, stats.profitPercent >= 0 ? styles.plValuePositive : styles.plValueNegative]}>
              {stats.profitPercent >= 0 ? '+' : ''}{(stats.profitPercent * 100).toFixed(2)}%
            </Text>
          </View>
          <View style={styles.plItem}>
            <Text style={styles.plLabel}>In {stats.fiat}</Text>
            <Text style={[styles.plValue, stats.profitPercent >= 0 ? styles.plValuePositive : styles.plValueNegative]}>
              {stats.profitPercent >= 0 ? '+' : ''}{stats.profitFiat.toFixed(2)}
            </Text>
          </View>
        </View>
      </View>

      {/* Averages */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Averages</Text>
        <View style={styles.averageRow}>
          <Text style={styles.averageLabel}>Per Transaction</Text>
          <Text style={styles.averageValue}>{avgInvestment.toFixed(2)} {stats.fiat}</Text>
        </View>
        <View style={styles.averageRow}>
          <Text style={styles.averageLabel}>Crypto per Buy</Text>
          <Text style={styles.averageValue}>{avgCrypto.toFixed(8)} {stats.currency}</Text>
        </View>
      </View>

      {/* Last Update */}
      <Text style={styles.lastUpdate}>
        Last updated: {new Date(stats.lastUpdate).toLocaleString()}
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#16213e',
  },
  content: {
    padding: 16,
  },
  emptyState: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 40,
  },
  emptyEmoji: {
    fontSize: 64,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    color: '#a0a0a0',
    textAlign: 'center',
    lineHeight: 20,
  },
  mainCard: {
    backgroundColor: '#1a1a2e',
    borderRadius: 16,
    padding: 24,
    alignItems: 'center',
    marginBottom: 16,
  },
  mainTitle: {
    fontSize: 14,
    color: '#a0a0a0',
    marginBottom: 8,
  },
  mainValue: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#4ecca3',
  },
  mainSubtitle: {
    fontSize: 16,
    color: '#fff',
    marginTop: 4,
  },
  statsRow: {
    flexDirection: 'row',
    marginBottom: 12,
    gap: 12,
  },
  statCard: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
  },
  statCardHalf: {
    flex: 1,
  },
  statLabel: {
    fontSize: 12,
    color: '#a0a0a0',
    marginBottom: 8,
  },
  statValue: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#fff',
  },
  statUnit: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
  },
  plCard: {
    borderRadius: 12,
    padding: 20,
    marginBottom: 16,
  },
  plPositive: {
    backgroundColor: 'rgba(78, 204, 163, 0.15)',
  },
  plNegative: {
    backgroundColor: 'rgba(233, 69, 96, 0.15)',
  },
  plRow: {
    flexDirection: 'row',
  },
  plItem: {
    flex: 1,
    alignItems: 'center',
  },
  plLabel: {
    fontSize: 12,
    color: '#a0a0a0',
    marginBottom: 8,
  },
  plValue: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  plValuePositive: {
    color: '#4ecca3',
  },
  plValueNegative: {
    color: '#e94560',
  },
  section: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 12,
  },
  averageRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#0f3460',
  },
  averageLabel: {
    fontSize: 14,
    color: '#a0a0a0',
  },
  averageValue: {
    fontSize: 14,
    color: '#fff',
    fontWeight: '500',
  },
  lastUpdate: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
    marginTop: 8,
    marginBottom: 24,
  },
});

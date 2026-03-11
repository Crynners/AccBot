import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
} from 'react-native';
import { Link } from 'expo-router';
import { useAccBotStore } from '../src/store/accbotStore';

export default function Dashboard() {
  const { isConfigured, stats, refreshStats, isLoading } = useAccBotStore();
  const [refreshing, setRefreshing] = React.useState(false);

  const onRefresh = React.useCallback(async () => {
    setRefreshing(true);
    await refreshStats();
    setRefreshing(false);
  }, [refreshStats]);

  if (!isConfigured) {
    return (
      <View style={styles.container}>
        <View style={styles.welcomeCard}>
          <Text style={styles.welcomeTitle}>Welcome to AccBot</Text>
          <Text style={styles.welcomeText}>
            Your automated DCA (Dollar Cost Averaging) bot for cryptocurrency accumulation.
          </Text>
          <Text style={styles.welcomeText}>
            Set up your bot to automatically buy crypto at regular intervals.
          </Text>
          <Link href="/wizard" asChild>
            <TouchableOpacity style={styles.primaryButton}>
              <Text style={styles.primaryButtonText}>Start Setup</Text>
            </TouchableOpacity>
          </Link>
        </View>

        <View style={styles.featureList}>
          <FeatureItem
            emoji="🔒"
            title="Self-Custody"
            description="Your keys, your crypto. Auto-withdrawal to your wallet."
          />
          <FeatureItem
            emoji="📊"
            title="DCA Strategy"
            description="Reduce volatility impact with regular purchases."
          />
          <FeatureItem
            emoji="🌐"
            title="Multi-Exchange"
            description="Supports Binance, Coinmate, Kraken, KuCoin & more."
          />
          <FeatureItem
            emoji="☁️"
            title="Cloud Hosted"
            description="Runs 24/7 on Azure Functions or Docker."
          />
        </View>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.scrollContent}
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={onRefresh}
          tintColor="#4ecca3"
        />
      }
    >
      {/* Status Card */}
      <View style={styles.statusCard}>
        <View style={styles.statusHeader}>
          <Text style={styles.statusEmoji}>✅</Text>
          <Text style={styles.statusTitle}>Bot Active</Text>
        </View>
        <Text style={styles.statusSubtitle}>
          {stats?.exchange || 'Loading...'} • {stats?.pair || 'BTC/EUR'}
        </Text>
      </View>

      {/* Stats Grid */}
      <View style={styles.statsGrid}>
        <StatCard
          title="Total Accumulated"
          value={stats ? `${stats.totalCrypto.toFixed(8)}` : '0.00000000'}
          subtitle={stats?.currency || 'BTC'}
          color="#4ecca3"
        />
        <StatCard
          title="Total Invested"
          value={stats ? `${stats.totalInvested.toFixed(2)}` : '0.00'}
          subtitle={stats?.fiat || 'EUR'}
          color="#0f3460"
        />
        <StatCard
          title="Avg Buy Price"
          value={stats ? `${stats.avgPrice.toFixed(0)}` : '0'}
          subtitle={stats?.fiat || 'EUR'}
          color="#e94560"
        />
        <StatCard
          title="Total Buys"
          value={stats?.totalBuys?.toString() || '0'}
          subtitle="transactions"
          color="#533483"
        />
      </View>

      {/* P/L Card */}
      {stats && (
        <View style={[styles.plCard, stats.profitPercent >= 0 ? styles.plPositive : styles.plNegative]}>
          <Text style={styles.plTitle}>Current P/L</Text>
          <Text style={styles.plValue}>
            {stats.profitPercent >= 0 ? '+' : ''}{(stats.profitPercent * 100).toFixed(2)}%
          </Text>
          <Text style={styles.plSubtitle}>
            {stats.profitPercent >= 0 ? '+' : ''}{stats.profitFiat.toFixed(2)} {stats.fiat}
          </Text>
        </View>
      )}

      {/* Quick Actions */}
      <View style={styles.actionsRow}>
        <Link href="/stats" asChild>
          <TouchableOpacity style={styles.actionButton}>
            <Text style={styles.actionEmoji}>📊</Text>
            <Text style={styles.actionText}>Stats</Text>
          </TouchableOpacity>
        </Link>
        <Link href="/settings" asChild>
          <TouchableOpacity style={styles.actionButton}>
            <Text style={styles.actionEmoji}>⚙️</Text>
            <Text style={styles.actionText}>Settings</Text>
          </TouchableOpacity>
        </Link>
        <TouchableOpacity style={styles.actionButton} onPress={onRefresh}>
          <Text style={styles.actionEmoji}>🔄</Text>
          <Text style={styles.actionText}>Refresh</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

function FeatureItem({ emoji, title, description }: { emoji: string; title: string; description: string }) {
  return (
    <View style={styles.featureItem}>
      <Text style={styles.featureEmoji}>{emoji}</Text>
      <View style={styles.featureTextContainer}>
        <Text style={styles.featureTitle}>{title}</Text>
        <Text style={styles.featureDescription}>{description}</Text>
      </View>
    </View>
  );
}

function StatCard({ title, value, subtitle, color }: { title: string; value: string; subtitle: string; color: string }) {
  return (
    <View style={[styles.statCard, { borderLeftColor: color }]}>
      <Text style={styles.statTitle}>{title}</Text>
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statSubtitle}>{subtitle}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#16213e',
  },
  scrollContent: {
    padding: 16,
  },
  welcomeCard: {
    backgroundColor: '#1a1a2e',
    borderRadius: 16,
    padding: 24,
    marginHorizontal: 16,
    marginTop: 24,
    alignItems: 'center',
  },
  welcomeTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 16,
  },
  welcomeText: {
    fontSize: 16,
    color: '#a0a0a0',
    textAlign: 'center',
    marginBottom: 12,
    lineHeight: 24,
  },
  primaryButton: {
    backgroundColor: '#4ecca3',
    paddingHorizontal: 32,
    paddingVertical: 14,
    borderRadius: 12,
    marginTop: 16,
  },
  primaryButtonText: {
    color: '#1a1a2e',
    fontSize: 18,
    fontWeight: 'bold',
  },
  featureList: {
    marginTop: 24,
    paddingHorizontal: 16,
  },
  featureItem: {
    flexDirection: 'row',
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  featureEmoji: {
    fontSize: 28,
    marginRight: 16,
  },
  featureTextContainer: {
    flex: 1,
  },
  featureTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 4,
  },
  featureDescription: {
    fontSize: 14,
    color: '#a0a0a0',
    lineHeight: 20,
  },
  statusCard: {
    backgroundColor: '#1a1a2e',
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    alignItems: 'center',
  },
  statusHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  statusEmoji: {
    fontSize: 24,
    marginRight: 8,
  },
  statusTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#4ecca3',
  },
  statusSubtitle: {
    fontSize: 14,
    color: '#a0a0a0',
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginHorizontal: -6,
  },
  statCard: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
    margin: 6,
    width: '47%',
    borderLeftWidth: 4,
  },
  statTitle: {
    fontSize: 12,
    color: '#a0a0a0',
    marginBottom: 8,
  },
  statValue: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#fff',
  },
  statSubtitle: {
    fontSize: 12,
    color: '#a0a0a0',
    marginTop: 4,
  },
  plCard: {
    borderRadius: 12,
    padding: 20,
    marginTop: 16,
    alignItems: 'center',
  },
  plPositive: {
    backgroundColor: 'rgba(78, 204, 163, 0.2)',
  },
  plNegative: {
    backgroundColor: 'rgba(233, 69, 96, 0.2)',
  },
  plTitle: {
    fontSize: 14,
    color: '#a0a0a0',
    marginBottom: 8,
  },
  plValue: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#fff',
  },
  plSubtitle: {
    fontSize: 16,
    color: '#a0a0a0',
    marginTop: 4,
  },
  actionsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginTop: 24,
    paddingBottom: 24,
  },
  actionButton: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    minWidth: 80,
  },
  actionEmoji: {
    fontSize: 24,
    marginBottom: 8,
  },
  actionText: {
    fontSize: 12,
    color: '#a0a0a0',
  },
});

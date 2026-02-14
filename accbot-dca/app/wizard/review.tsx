import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { router } from 'expo-router';
import { useAccBotStore, EXCHANGES, HOUR_DIVIDERS } from '../../src/store/accbotStore';

export default function ReviewDeploy() {
  const { config, setConfig } = useAccBotStore();
  const [isDeploying, setIsDeploying] = useState(false);
  const [deploymentTarget, setDeploymentTarget] = useState<'azure' | 'docker'>(config?.deploymentTarget || 'azure');

  const exchangeInfo = EXCHANGES.find(e => e.id === config?.exchange);
  const frequencyInfo = HOUR_DIVIDERS.find(h => h.value === config?.hourDivider);

  const handleDeploy = async () => {
    if (!config) return;

    setIsDeploying(true);

    try {
      // Save the final config
      setConfig({
        ...config,
        deploymentTarget,
      });

      // Simulate deployment
      await new Promise(resolve => setTimeout(resolve, 2000));

      Alert.alert(
        'Success! 🎉',
        `Your AccBot is configured and ready!\n\n${
          deploymentTarget === 'azure'
            ? 'Follow the deployment guide to deploy to Azure.'
            : 'Run the Docker container to start your bot.'
        }`,
        [
          {
            text: 'Go to Dashboard',
            onPress: () => router.replace('/'),
          },
        ]
      );
    } catch (error) {
      Alert.alert('Error', 'Failed to save configuration. Please try again.');
    } finally {
      setIsDeploying(false);
    }
  };

  if (!config) {
    return (
      <View style={styles.container}>
        <Text style={styles.errorText}>Configuration not found. Please start over.</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        <Text style={styles.title}>Review & Deploy</Text>
        <Text style={styles.subtitle}>
          Review your configuration before deploying
        </Text>

        {/* Configuration Summary */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>📊 Trading Configuration</Text>

          <View style={styles.configRow}>
            <Text style={styles.configLabel}>Exchange</Text>
            <Text style={styles.configValue}>{exchangeInfo?.name}</Text>
          </View>

          <View style={styles.configRow}>
            <Text style={styles.configLabel}>Trading Pair</Text>
            <Text style={styles.configValue}>{config.currency}/{config.fiat}</Text>
          </View>

          <View style={styles.configRow}>
            <Text style={styles.configLabel}>Amount</Text>
            <Text style={styles.configValue}>{config.chunkSize} {config.fiat}</Text>
          </View>

          <View style={styles.configRow}>
            <Text style={styles.configLabel}>Frequency</Text>
            <Text style={styles.configValue}>{frequencyInfo?.label}</Text>
          </View>

          <View style={styles.configRow}>
            <Text style={styles.configLabel}>Auto-Withdrawal</Text>
            <Text style={[styles.configValue, config.withdrawalEnabled ? styles.configValueEnabled : styles.configValueDisabled]}>
              {config.withdrawalEnabled ? 'Enabled' : 'Disabled'}
            </Text>
          </View>

          {config.withdrawalEnabled && config.withdrawalAddress && (
            <View style={styles.configRow}>
              <Text style={styles.configLabel}>Wallet</Text>
              <Text style={styles.configValueSmall} numberOfLines={1}>
                {config.withdrawalAddress.substring(0, 12)}...{config.withdrawalAddress.slice(-8)}
              </Text>
            </View>
          )}
        </View>

        {/* Deployment Target */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>🚀 Deployment Target</Text>

          <TouchableOpacity
            style={[styles.deployOption, deploymentTarget === 'azure' && styles.deployOptionSelected]}
            onPress={() => setDeploymentTarget('azure')}
          >
            <View style={styles.deployOptionIcon}>
              <Text style={styles.deployOptionEmoji}>☁️</Text>
            </View>
            <View style={styles.deployOptionContent}>
              <Text style={styles.deployOptionTitle}>Azure Functions</Text>
              <Text style={styles.deployOptionDescription}>
                Serverless hosting on Microsoft Azure. ~$0.04/month
              </Text>
            </View>
            <View style={[styles.radio, deploymentTarget === 'azure' && styles.radioSelected]}>
              {deploymentTarget === 'azure' && <View style={styles.radioInner} />}
            </View>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.deployOption, deploymentTarget === 'docker' && styles.deployOptionSelected]}
            onPress={() => setDeploymentTarget('docker')}
          >
            <View style={styles.deployOptionIcon}>
              <Text style={styles.deployOptionEmoji}>🐳</Text>
            </View>
            <View style={styles.deployOptionContent}>
              <Text style={styles.deployOptionTitle}>Docker</Text>
              <Text style={styles.deployOptionDescription}>
                Run on your own server. Free hosting.
              </Text>
            </View>
            <View style={[styles.radio, deploymentTarget === 'docker' && styles.radioSelected]}>
              {deploymentTarget === 'docker' && <View style={styles.radioInner} />}
            </View>
          </TouchableOpacity>
        </View>

        {/* Estimated Cost */}
        <View style={styles.costCard}>
          <Text style={styles.costTitle}>💰 Estimated Monthly Cost</Text>
          <View style={styles.costRow}>
            <Text style={styles.costLabel}>DCA Purchases</Text>
            <Text style={styles.costValue}>
              ~{(config.chunkSize * (720 / config.hourDivider)).toFixed(0)} {config.fiat}
            </Text>
          </View>
          <View style={styles.costRow}>
            <Text style={styles.costLabel}>Hosting</Text>
            <Text style={styles.costValue}>
              {deploymentTarget === 'azure' ? '~$0.04' : 'Free'}
            </Text>
          </View>
          <View style={styles.costRow}>
            <Text style={styles.costLabel}>Trading Fees</Text>
            <Text style={styles.costValue}>~0.1-0.3%</Text>
          </View>
        </View>

        {/* Warning */}
        <View style={styles.warningCard}>
          <Text style={styles.warningTitle}>⚠️ Important</Text>
          <Text style={styles.warningText}>
            This app helps you configure AccBot. You will need to deploy it yourself using the provided configuration.
          </Text>
          <Text style={styles.warningText}>
            Your API keys are stored locally on this device only.
          </Text>
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
          <Text style={styles.backButtonText}>← Back</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.deployButton, isDeploying && styles.deployButtonDisabled]}
          onPress={handleDeploy}
          disabled={isDeploying}
        >
          {isDeploying ? (
            <ActivityIndicator color="#1a1a2e" />
          ) : (
            <Text style={styles.deployButtonText}>Save Config ✓</Text>
          )}
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#16213e',
  },
  scrollView: {
    flex: 1,
  },
  content: {
    padding: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#a0a0a0',
    marginBottom: 24,
  },
  errorText: {
    color: '#e94560',
    padding: 20,
    textAlign: 'center',
  },
  card: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 16,
  },
  configRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#0f3460',
  },
  configLabel: {
    fontSize: 14,
    color: '#a0a0a0',
  },
  configValue: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
  },
  configValueSmall: {
    fontSize: 12,
    fontWeight: '600',
    color: '#fff',
    maxWidth: 150,
  },
  configValueEnabled: {
    color: '#4ecca3',
  },
  configValueDisabled: {
    color: '#666',
  },
  deployOption: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 14,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#0f3460',
    marginBottom: 10,
  },
  deployOptionSelected: {
    borderColor: '#4ecca3',
    backgroundColor: 'rgba(78, 204, 163, 0.05)',
  },
  deployOptionIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#0f3460',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  deployOptionEmoji: {
    fontSize: 20,
  },
  deployOptionContent: {
    flex: 1,
  },
  deployOptionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 2,
  },
  deployOptionDescription: {
    fontSize: 12,
    color: '#a0a0a0',
  },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: '#666',
    justifyContent: 'center',
    alignItems: 'center',
  },
  radioSelected: {
    borderColor: '#4ecca3',
  },
  radioInner: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: '#4ecca3',
  },
  costCard: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  costTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#4ecca3',
    marginBottom: 12,
  },
  costRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  costLabel: {
    fontSize: 13,
    color: '#a0a0a0',
  },
  costValue: {
    fontSize: 13,
    color: '#fff',
    fontWeight: '500',
  },
  warningCard: {
    backgroundColor: 'rgba(233, 69, 96, 0.1)',
    borderRadius: 12,
    padding: 16,
    borderLeftWidth: 4,
    borderLeftColor: '#e94560',
  },
  warningTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#e94560',
    marginBottom: 8,
  },
  warningText: {
    fontSize: 12,
    color: '#a0a0a0',
    lineHeight: 18,
    marginBottom: 8,
  },
  footer: {
    flexDirection: 'row',
    padding: 20,
    backgroundColor: '#1a1a2e',
    borderTopWidth: 1,
    borderTopColor: '#0f3460',
  },
  backButton: {
    flex: 1,
    paddingVertical: 14,
    alignItems: 'center',
    marginRight: 10,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#4ecca3',
  },
  backButtonText: {
    fontSize: 16,
    color: '#4ecca3',
    fontWeight: '600',
  },
  deployButton: {
    flex: 1,
    paddingVertical: 14,
    alignItems: 'center',
    marginLeft: 10,
    borderRadius: 12,
    backgroundColor: '#4ecca3',
  },
  deployButtonDisabled: {
    backgroundColor: '#333',
  },
  deployButtonText: {
    fontSize: 16,
    color: '#1a1a2e',
    fontWeight: '600',
  },
});

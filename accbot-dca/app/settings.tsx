import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  Linking,
} from 'react-native';
import { router } from 'expo-router';
import { useAccBotStore, EXCHANGES } from '../src/store/accbotStore';

export default function SettingsScreen() {
  const { config, clearConfig } = useAccBotStore();
  const exchangeInfo = EXCHANGES.find(e => e.id === config?.exchange);

  const handleResetConfig = () => {
    Alert.alert(
      'Reset Configuration',
      'Are you sure you want to clear all settings? This cannot be undone.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reset',
          style: 'destructive',
          onPress: () => {
            clearConfig();
            router.replace('/');
          },
        },
      ]
    );
  };

  const handleOpenDocs = () => {
    Linking.openURL('https://github.com/crynners/AccBot');
  };

  const handleEditConfig = () => {
    router.push('/wizard/exchange');
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {/* Current Config */}
      {config && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Current Configuration</Text>

          <View style={styles.configCard}>
            <View style={styles.configRow}>
              <Text style={styles.configLabel}>Exchange</Text>
              <Text style={styles.configValue}>{exchangeInfo?.name || config.exchange}</Text>
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
              <Text style={styles.configValue}>Every {config.hourDivider}h</Text>
            </View>
            <View style={styles.configRow}>
              <Text style={styles.configLabel}>Withdrawal</Text>
              <Text style={[styles.configValue, config.withdrawalEnabled ? styles.enabled : styles.disabled]}>
                {config.withdrawalEnabled ? 'Enabled' : 'Disabled'}
              </Text>
            </View>
            <View style={styles.configRow}>
              <Text style={styles.configLabel}>Deployment</Text>
              <Text style={styles.configValue}>
                {config.deploymentTarget === 'azure' ? 'Azure Functions' : 'Docker'}
              </Text>
            </View>
          </View>

          <TouchableOpacity style={styles.editButton} onPress={handleEditConfig}>
            <Text style={styles.editButtonText}>Edit Configuration</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Actions */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Actions</Text>

        <TouchableOpacity style={styles.actionItem} onPress={handleOpenDocs}>
          <View style={styles.actionIcon}>
            <Text style={styles.actionEmoji}>📚</Text>
          </View>
          <View style={styles.actionContent}>
            <Text style={styles.actionTitle}>Documentation</Text>
            <Text style={styles.actionDescription}>View setup guides and API docs</Text>
          </View>
          <Text style={styles.actionArrow}>→</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.actionItem}
          onPress={() => Linking.openURL('https://github.com/crynners/AccBot/issues')}
        >
          <View style={styles.actionIcon}>
            <Text style={styles.actionEmoji}>🐛</Text>
          </View>
          <View style={styles.actionContent}>
            <Text style={styles.actionTitle}>Report Issue</Text>
            <Text style={styles.actionDescription}>Found a bug? Let us know</Text>
          </View>
          <Text style={styles.actionArrow}>→</Text>
        </TouchableOpacity>
      </View>

      {/* Danger Zone */}
      <View style={styles.section}>
        <Text style={styles.sectionTitleDanger}>Danger Zone</Text>

        <TouchableOpacity style={styles.dangerButton} onPress={handleResetConfig}>
          <Text style={styles.dangerButtonText}>Reset All Settings</Text>
        </TouchableOpacity>

        <Text style={styles.dangerHint}>
          This will clear all your configuration including API credentials stored on this device.
        </Text>
      </View>

      {/* App Info */}
      <View style={styles.footer}>
        <Text style={styles.footerTitle}>AccBot DCA</Text>
        <Text style={styles.footerVersion}>Version 1.0.0</Text>
        <Text style={styles.footerCopyright}>Made with ❤️ by Crynners</Text>
      </View>
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
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#a0a0a0',
    marginBottom: 12,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  sectionTitleDanger: {
    fontSize: 14,
    fontWeight: '600',
    color: '#e94560',
    marginBottom: 12,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  configCard: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
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
    fontWeight: '500',
    color: '#fff',
  },
  enabled: {
    color: '#4ecca3',
  },
  disabled: {
    color: '#666',
  },
  editButton: {
    backgroundColor: '#0f3460',
    borderRadius: 10,
    padding: 14,
    alignItems: 'center',
    marginTop: 12,
  },
  editButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#4ecca3',
  },
  actionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
  },
  actionIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#0f3460',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  actionEmoji: {
    fontSize: 18,
  },
  actionContent: {
    flex: 1,
  },
  actionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 2,
  },
  actionDescription: {
    fontSize: 12,
    color: '#a0a0a0',
  },
  actionArrow: {
    fontSize: 18,
    color: '#666',
  },
  dangerButton: {
    backgroundColor: 'rgba(233, 69, 96, 0.15)',
    borderRadius: 10,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e94560',
  },
  dangerButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#e94560',
  },
  dangerHint: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
    marginTop: 12,
    lineHeight: 18,
  },
  footer: {
    alignItems: 'center',
    paddingVertical: 24,
    borderTopWidth: 1,
    borderTopColor: '#0f3460',
    marginTop: 8,
  },
  footerTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#fff',
  },
  footerVersion: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
  },
  footerCopyright: {
    fontSize: 12,
    color: '#a0a0a0',
    marginTop: 8,
  },
});

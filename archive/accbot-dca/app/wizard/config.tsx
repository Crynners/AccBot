import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TextInput,
  ScrollView,
  Switch,
} from 'react-native';
import { router } from 'expo-router';
import { useAccBotStore, CURRENCIES, HOUR_DIVIDERS, EXCHANGES } from '../../src/store/accbotStore';

export default function DCAConfig() {
  const { config, setConfig } = useAccBotStore();
  const [currency, setCurrency] = useState(config?.currency || 'BTC');
  const [fiat, setFiat] = useState(config?.fiat || 'EUR');
  const [chunkSize, setChunkSize] = useState(config?.chunkSize?.toString() || '100');
  const [hourDivider, setHourDivider] = useState(config?.hourDivider || 24);
  const [withdrawalEnabled, setWithdrawalEnabled] = useState(config?.withdrawalEnabled || false);
  const [withdrawalAddress, setWithdrawalAddress] = useState(config?.withdrawalAddress || '');

  const exchangeInfo = EXCHANGES.find(e => e.id === config?.exchange);
  const availableFiats = exchangeInfo?.fiats || ['EUR'];

  const isValid = parseInt(chunkSize) > 0 && (!withdrawalEnabled || withdrawalAddress.trim().length > 0);

  const handleNext = () => {
    if (!config || !isValid) return;

    setConfig({
      ...config,
      currency,
      fiat,
      chunkSize: parseInt(chunkSize),
      hourDivider,
      withdrawalEnabled,
      withdrawalAddress: withdrawalEnabled ? withdrawalAddress.trim() : undefined,
    });
    router.push('/wizard/review');
  };

  return (
    <View style={styles.container}>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        <Text style={styles.title}>DCA Configuration</Text>
        <Text style={styles.subtitle}>
          Configure your Dollar Cost Averaging strategy
        </Text>

        {/* Currency Selection */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Cryptocurrency</Text>
          <View style={styles.optionsRow}>
            {CURRENCIES.map((c) => (
              <TouchableOpacity
                key={c}
                style={[styles.optionButton, currency === c && styles.optionButtonSelected]}
                onPress={() => setCurrency(c)}
              >
                <Text style={[styles.optionText, currency === c && styles.optionTextSelected]}>
                  {c}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Fiat Selection */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Fiat Currency</Text>
          <View style={styles.optionsRow}>
            {availableFiats.map((f) => (
              <TouchableOpacity
                key={f}
                style={[styles.optionButton, fiat === f && styles.optionButtonSelected]}
                onPress={() => setFiat(f)}
              >
                <Text style={[styles.optionText, fiat === f && styles.optionTextSelected]}>
                  {f}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Chunk Size */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Amount per Purchase</Text>
          <View style={styles.amountInputContainer}>
            <TextInput
              style={styles.amountInput}
              value={chunkSize}
              onChangeText={setChunkSize}
              keyboardType="numeric"
              placeholder="100"
              placeholderTextColor="#666"
            />
            <Text style={styles.amountCurrency}>{fiat}</Text>
          </View>
          <Text style={styles.hint}>How much to spend on each buy order</Text>
        </View>

        {/* Frequency */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Purchase Frequency</Text>
          <View style={styles.frequencyList}>
            {HOUR_DIVIDERS.map((h) => (
              <TouchableOpacity
                key={h.value}
                style={[styles.frequencyItem, hourDivider === h.value && styles.frequencyItemSelected]}
                onPress={() => setHourDivider(h.value)}
              >
                <View style={[styles.radio, hourDivider === h.value && styles.radioSelected]}>
                  {hourDivider === h.value && <View style={styles.radioInner} />}
                </View>
                <Text style={[styles.frequencyText, hourDivider === h.value && styles.frequencyTextSelected]}>
                  {h.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Withdrawal */}
        <View style={styles.section}>
          <View style={styles.switchRow}>
            <View style={styles.switchInfo}>
              <Text style={styles.sectionTitle}>Auto-Withdrawal</Text>
              <Text style={styles.hint}>Automatically send to your wallet</Text>
            </View>
            <Switch
              value={withdrawalEnabled}
              onValueChange={setWithdrawalEnabled}
              trackColor={{ false: '#333', true: '#4ecca3' }}
              thumbColor={withdrawalEnabled ? '#fff' : '#666'}
            />
          </View>

          {withdrawalEnabled && (
            <View style={styles.withdrawalSection}>
              <Text style={styles.label}>Wallet Address</Text>
              <TextInput
                style={styles.input}
                value={withdrawalAddress}
                onChangeText={setWithdrawalAddress}
                placeholder={`Your ${currency} wallet address`}
                placeholderTextColor="#666"
                autoCapitalize="none"
                autoCorrect={false}
              />
              <View style={styles.infoCard}>
                <Text style={styles.infoText}>
                  Withdrawals happen automatically when the fee is below 0.1% of your balance.
                </Text>
              </View>
            </View>
          )}
        </View>

        {/* Summary */}
        <View style={styles.summaryCard}>
          <Text style={styles.summaryTitle}>📊 Summary</Text>
          <Text style={styles.summaryText}>
            Buy <Text style={styles.summaryHighlight}>{chunkSize} {fiat}</Text> of{' '}
            <Text style={styles.summaryHighlight}>{currency}</Text>{' '}
            {HOUR_DIVIDERS.find(h => h.value === hourDivider)?.label.toLowerCase()}
          </Text>
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
          <Text style={styles.backButtonText}>← Back</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.nextButton, !isValid && styles.nextButtonDisabled]}
          onPress={handleNext}
          disabled={!isValid}
        >
          <Text style={[styles.nextButtonText, !isValid && styles.nextButtonTextDisabled]}>
            Review →
          </Text>
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
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 12,
  },
  optionsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  optionButton: {
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 8,
    backgroundColor: '#1a1a2e',
    borderWidth: 1,
    borderColor: '#0f3460',
  },
  optionButtonSelected: {
    backgroundColor: '#4ecca3',
    borderColor: '#4ecca3',
  },
  optionText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#a0a0a0',
  },
  optionTextSelected: {
    color: '#1a1a2e',
  },
  amountInputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#0f3460',
  },
  amountInput: {
    flex: 1,
    padding: 16,
    fontSize: 18,
    color: '#fff',
  },
  amountCurrency: {
    paddingRight: 16,
    fontSize: 16,
    color: '#4ecca3',
    fontWeight: '600',
  },
  hint: {
    fontSize: 12,
    color: '#666',
    marginTop: 8,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 8,
  },
  input: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
    fontSize: 14,
    color: '#fff',
    borderWidth: 1,
    borderColor: '#0f3460',
  },
  frequencyList: {
    gap: 8,
  },
  frequencyItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1a1a2e',
    padding: 14,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'transparent',
  },
  frequencyItemSelected: {
    borderColor: '#4ecca3',
  },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: '#666',
    marginRight: 12,
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
  frequencyText: {
    fontSize: 14,
    color: '#a0a0a0',
  },
  frequencyTextSelected: {
    color: '#fff',
    fontWeight: '500',
  },
  switchRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  switchInfo: {
    flex: 1,
  },
  withdrawalSection: {
    marginTop: 8,
  },
  infoCard: {
    backgroundColor: 'rgba(78, 204, 163, 0.1)',
    borderRadius: 8,
    padding: 12,
    marginTop: 12,
  },
  infoText: {
    fontSize: 12,
    color: '#a0a0a0',
    lineHeight: 18,
  },
  summaryCard: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
    borderLeftWidth: 4,
    borderLeftColor: '#4ecca3',
  },
  summaryTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#4ecca3',
    marginBottom: 8,
  },
  summaryText: {
    fontSize: 14,
    color: '#a0a0a0',
    lineHeight: 22,
  },
  summaryHighlight: {
    color: '#fff',
    fontWeight: '600',
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
  nextButton: {
    flex: 1,
    paddingVertical: 14,
    alignItems: 'center',
    marginLeft: 10,
    borderRadius: 12,
    backgroundColor: '#4ecca3',
  },
  nextButtonDisabled: {
    backgroundColor: '#333',
  },
  nextButtonText: {
    fontSize: 16,
    color: '#1a1a2e',
    fontWeight: '600',
  },
  nextButtonTextDisabled: {
    color: '#666',
  },
});

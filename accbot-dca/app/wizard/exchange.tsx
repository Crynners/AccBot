import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { router } from 'expo-router';
import { EXCHANGES, useAccBotStore } from '../../src/store/accbotStore';

export default function ExchangeSelection() {
  const { config, setConfig } = useAccBotStore();
  const [selectedExchange, setSelectedExchange] = useState(config?.exchange || 'coinmate');

  const handleNext = () => {
    setConfig({
      ...(config || {
        apiKey: '',
        apiSecret: '',
        currency: 'BTC',
        fiat: 'EUR',
        chunkSize: 100,
        hourDivider: 24,
        withdrawalEnabled: false,
        maxWithdrawalPercentageFee: 0.001,
        deploymentTarget: 'azure',
      }),
      exchange: selectedExchange,
    });
    router.push('/wizard/credentials');
  };

  return (
    <View style={styles.container}>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        <Text style={styles.title}>Select Your Exchange</Text>
        <Text style={styles.subtitle}>
          Choose the exchange where you have an account and want to run your DCA strategy
        </Text>

        <View style={styles.exchangeList}>
          {EXCHANGES.map((exchange) => (
            <TouchableOpacity
              key={exchange.id}
              style={[
                styles.exchangeCard,
                selectedExchange === exchange.id && styles.exchangeCardSelected,
              ]}
              onPress={() => setSelectedExchange(exchange.id)}
            >
              <View style={styles.exchangeRadio}>
                <View
                  style={[
                    styles.radioOuter,
                    selectedExchange === exchange.id && styles.radioOuterSelected,
                  ]}
                >
                  {selectedExchange === exchange.id && <View style={styles.radioInner} />}
                </View>
              </View>
              <View style={styles.exchangeInfo}>
                <Text style={styles.exchangeName}>{exchange.name}</Text>
                <Text style={styles.exchangeDescription}>{exchange.description}</Text>
                <View style={styles.fiatsRow}>
                  {exchange.fiats.map((fiat) => (
                    <View key={fiat} style={styles.fiatBadge}>
                      <Text style={styles.fiatText}>{fiat}</Text>
                    </View>
                  ))}
                </View>
              </View>
            </TouchableOpacity>
          ))}
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
          <Text style={styles.backButtonText}>← Back</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.nextButton} onPress={handleNext}>
          <Text style={styles.nextButtonText}>Next →</Text>
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
    lineHeight: 20,
  },
  exchangeList: {
    gap: 12,
  },
  exchangeCard: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
    flexDirection: 'row',
    borderWidth: 2,
    borderColor: 'transparent',
  },
  exchangeCardSelected: {
    borderColor: '#4ecca3',
  },
  exchangeRadio: {
    marginRight: 16,
    paddingTop: 2,
  },
  radioOuter: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: '#a0a0a0',
    justifyContent: 'center',
    alignItems: 'center',
  },
  radioOuterSelected: {
    borderColor: '#4ecca3',
  },
  radioInner: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#4ecca3',
  },
  exchangeInfo: {
    flex: 1,
  },
  exchangeName: {
    fontSize: 18,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 4,
  },
  exchangeDescription: {
    fontSize: 14,
    color: '#a0a0a0',
    marginBottom: 8,
  },
  fiatsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  fiatBadge: {
    backgroundColor: '#0f3460',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  fiatText: {
    fontSize: 12,
    color: '#4ecca3',
    fontWeight: '500',
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
  nextButtonText: {
    fontSize: 16,
    color: '#1a1a2e',
    fontWeight: '600',
  },
});

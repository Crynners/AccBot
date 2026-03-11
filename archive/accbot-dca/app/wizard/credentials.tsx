import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TextInput,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { router } from 'expo-router';
import { useAccBotStore, EXCHANGES } from '../../src/store/accbotStore';

export default function CredentialsInput() {
  const { config, setConfig } = useAccBotStore();
  const [apiKey, setApiKey] = useState(config?.apiKey || '');
  const [apiSecret, setApiSecret] = useState(config?.apiSecret || '');
  const [passphrase, setPassphrase] = useState(config?.passphrase || '');
  const [showSecret, setShowSecret] = useState(false);

  const exchangeInfo = EXCHANGES.find(e => e.id === config?.exchange);
  const needsPassphrase = config?.exchange === 'kucoin';

  const isValid = apiKey.trim().length > 0 && apiSecret.trim().length > 0 &&
    (!needsPassphrase || passphrase.trim().length > 0);

  const handleNext = () => {
    if (!config || !isValid) return;

    setConfig({
      ...config,
      apiKey: apiKey.trim(),
      apiSecret: apiSecret.trim(),
      passphrase: needsPassphrase ? passphrase.trim() : undefined,
    });
    router.push('/wizard/config');
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        <Text style={styles.title}>API Credentials</Text>
        <Text style={styles.subtitle}>
          Enter your {exchangeInfo?.name || 'exchange'} API credentials
        </Text>

        <View style={styles.infoCard}>
          <Text style={styles.infoTitle}>🔒 Security Tips</Text>
          <Text style={styles.infoText}>• Only enable trading permissions</Text>
          <Text style={styles.infoText}>• Disable withdrawal if not needed</Text>
          <Text style={styles.infoText}>• Use IP whitelist when available</Text>
        </View>

        <View style={styles.inputGroup}>
          <Text style={styles.label}>API Key</Text>
          <TextInput
            style={styles.input}
            value={apiKey}
            onChangeText={setApiKey}
            placeholder="Enter your API key"
            placeholderTextColor="#666"
            autoCapitalize="none"
            autoCorrect={false}
          />
        </View>

        <View style={styles.inputGroup}>
          <Text style={styles.label}>API Secret</Text>
          <View style={styles.secretInputContainer}>
            <TextInput
              style={[styles.input, styles.secretInput]}
              value={apiSecret}
              onChangeText={setApiSecret}
              placeholder="Enter your API secret"
              placeholderTextColor="#666"
              secureTextEntry={!showSecret}
              autoCapitalize="none"
              autoCorrect={false}
            />
            <TouchableOpacity
              style={styles.showButton}
              onPress={() => setShowSecret(!showSecret)}
            >
              <Text style={styles.showButtonText}>{showSecret ? '🙈' : '👁️'}</Text>
            </TouchableOpacity>
          </View>
        </View>

        {needsPassphrase && (
          <View style={styles.inputGroup}>
            <Text style={styles.label}>Passphrase</Text>
            <TextInput
              style={styles.input}
              value={passphrase}
              onChangeText={setPassphrase}
              placeholder="Enter your API passphrase"
              placeholderTextColor="#666"
              secureTextEntry={!showSecret}
              autoCapitalize="none"
              autoCorrect={false}
            />
            <Text style={styles.hint}>Required for KuCoin API</Text>
          </View>
        )}

        <View style={styles.helpCard}>
          <Text style={styles.helpTitle}>📖 How to get API credentials</Text>
          <Text style={styles.helpText}>
            1. Log in to {exchangeInfo?.name || 'your exchange'}{'\n'}
            2. Go to API Management / Settings{'\n'}
            3. Create a new API key{'\n'}
            4. Enable "Spot Trading" permission{'\n'}
            5. Copy the key and secret here
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
            Next →
          </Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
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
  infoCard: {
    backgroundColor: 'rgba(78, 204, 163, 0.1)',
    borderRadius: 12,
    padding: 16,
    marginBottom: 24,
    borderLeftWidth: 4,
    borderLeftColor: '#4ecca3',
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#4ecca3',
    marginBottom: 8,
  },
  infoText: {
    fontSize: 14,
    color: '#a0a0a0',
    lineHeight: 22,
  },
  inputGroup: {
    marginBottom: 20,
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
    fontSize: 16,
    color: '#fff',
    borderWidth: 1,
    borderColor: '#0f3460',
  },
  secretInputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  secretInput: {
    flex: 1,
    borderTopRightRadius: 0,
    borderBottomRightRadius: 0,
  },
  showButton: {
    backgroundColor: '#1a1a2e',
    padding: 16,
    borderTopRightRadius: 12,
    borderBottomRightRadius: 12,
    borderWidth: 1,
    borderColor: '#0f3460',
    borderLeftWidth: 0,
  },
  showButtonText: {
    fontSize: 18,
  },
  hint: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
  },
  helpCard: {
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    padding: 16,
    marginTop: 8,
  },
  helpTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 12,
  },
  helpText: {
    fontSize: 13,
    color: '#a0a0a0',
    lineHeight: 22,
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

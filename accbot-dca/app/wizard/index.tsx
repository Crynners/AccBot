import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { Link } from 'expo-router';

export default function WizardStart() {
  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <Text style={styles.emoji}>🚀</Text>
        <Text style={styles.title}>Setup Your Bot</Text>
        <Text style={styles.subtitle}>
          Let's configure your DCA bot in a few simple steps
        </Text>
      </View>

      <View style={styles.stepsOverview}>
        <StepPreview number={1} title="Select Exchange" description="Choose where to trade" />
        <StepPreview number={2} title="API Credentials" description="Connect your account" />
        <StepPreview number={3} title="DCA Settings" description="Configure your strategy" />
        <StepPreview number={4} title="Review & Deploy" description="Activate your bot" />
      </View>

      <View style={styles.infoCard}>
        <Text style={styles.infoTitle}>🔒 Security First</Text>
        <Text style={styles.infoText}>
          Your API keys are stored locally on your device. We never store your credentials on any server.
        </Text>
        <Text style={styles.infoText}>
          For maximum security, create API keys with trading-only permissions (no withdrawal if not needed).
        </Text>
      </View>

      <Link href="/wizard/exchange" asChild>
        <TouchableOpacity style={styles.startButton}>
          <Text style={styles.startButtonText}>Let's Get Started</Text>
          <Text style={styles.startButtonArrow}>→</Text>
        </TouchableOpacity>
      </Link>
    </ScrollView>
  );
}

function StepPreview({ number, title, description }: { number: number; title: string; description: string }) {
  return (
    <View style={styles.stepItem}>
      <View style={styles.stepNumber}>
        <Text style={styles.stepNumberText}>{number}</Text>
      </View>
      <View style={styles.stepContent}>
        <Text style={styles.stepTitle}>{title}</Text>
        <Text style={styles.stepDescription}>{description}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#16213e',
  },
  content: {
    padding: 20,
  },
  header: {
    alignItems: 'center',
    marginBottom: 32,
  },
  emoji: {
    fontSize: 64,
    marginBottom: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#a0a0a0',
    textAlign: 'center',
  },
  stepsOverview: {
    backgroundColor: '#1a1a2e',
    borderRadius: 16,
    padding: 20,
    marginBottom: 20,
  },
  stepItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  stepNumber: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: '#4ecca3',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  stepNumberText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a1a2e',
  },
  stepContent: {
    flex: 1,
  },
  stepTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
  },
  stepDescription: {
    fontSize: 14,
    color: '#a0a0a0',
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
    lineHeight: 20,
    marginBottom: 8,
  },
  startButton: {
    backgroundColor: '#4ecca3',
    borderRadius: 12,
    paddingVertical: 16,
    paddingHorizontal: 24,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  startButtonText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a1a2e',
  },
  startButtonArrow: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a1a2e',
    marginLeft: 8,
  },
});

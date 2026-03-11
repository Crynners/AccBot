import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      <Stack
        screenOptions={{
          headerStyle: {
            backgroundColor: '#1a1a2e',
          },
          headerTintColor: '#fff',
          headerTitleStyle: {
            fontWeight: 'bold',
          },
          contentStyle: {
            backgroundColor: '#16213e',
          },
        }}
      >
        <Stack.Screen
          name="index"
          options={{
            title: 'AccBot',
            headerShown: true,
          }}
        />
        <Stack.Screen
          name="wizard/index"
          options={{
            title: 'Setup Wizard',
            headerShown: true,
          }}
        />
        <Stack.Screen
          name="wizard/exchange"
          options={{
            title: 'Select Exchange',
          }}
        />
        <Stack.Screen
          name="wizard/credentials"
          options={{
            title: 'API Credentials',
          }}
        />
        <Stack.Screen
          name="wizard/config"
          options={{
            title: 'DCA Configuration',
          }}
        />
        <Stack.Screen
          name="wizard/review"
          options={{
            title: 'Review & Deploy',
          }}
        />
        <Stack.Screen
          name="stats"
          options={{
            title: 'Statistics',
          }}
        />
        <Stack.Screen
          name="settings"
          options={{
            title: 'Settings',
          }}
        />
      </Stack>
    </SafeAreaProvider>
  );
}

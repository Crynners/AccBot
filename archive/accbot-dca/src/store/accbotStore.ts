import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import AsyncStorage from '@react-native-async-storage/async-storage';

export interface BotStats {
  exchange: string;
  pair: string;
  currency: string;
  fiat: string;
  totalCrypto: number;
  totalInvested: number;
  avgPrice: number;
  currentPrice: number;
  totalBuys: number;
  profitPercent: number;
  profitFiat: number;
  lastUpdate: string;
}

export interface BotConfig {
  exchange: string;
  apiKey: string;
  apiSecret: string;
  passphrase?: string;
  currency: string;
  fiat: string;
  chunkSize: number;
  hourDivider: number;
  withdrawalEnabled: boolean;
  withdrawalAddress?: string;
  maxWithdrawalPercentageFee: number;
  telegramBotToken?: string;
  telegramChannelId?: string;
  deploymentTarget: 'azure' | 'docker' | 'aws' | 'gcp';
  azureLocation?: string;
}

interface AccBotState {
  // Configuration
  isConfigured: boolean;
  config: BotConfig | null;

  // Stats
  stats: BotStats | null;
  isLoading: boolean;
  error: string | null;

  // Actions
  setConfig: (config: BotConfig) => void;
  clearConfig: () => void;
  refreshStats: () => Promise<void>;
  setStats: (stats: BotStats) => void;
}

const defaultConfig: BotConfig = {
  exchange: 'coinmate',
  apiKey: '',
  apiSecret: '',
  currency: 'BTC',
  fiat: 'EUR',
  chunkSize: 100,
  hourDivider: 24,
  withdrawalEnabled: false,
  maxWithdrawalPercentageFee: 0.001,
  deploymentTarget: 'azure',
  azureLocation: 'germanywestcentral',
};

export const useAccBotStore = create<AccBotState>()(
  persist(
    (set, get) => ({
      isConfigured: false,
      config: null,
      stats: null,
      isLoading: false,
      error: null,

      setConfig: (config) => {
        set({ config, isConfigured: true });
      },

      clearConfig: () => {
        set({ config: null, isConfigured: false, stats: null });
      },

      refreshStats: async () => {
        const { config } = get();
        if (!config) return;

        set({ isLoading: true, error: null });

        try {
          // In a real implementation, this would fetch from your backend API
          // For now, we'll simulate with mock data
          const mockStats: BotStats = {
            exchange: config.exchange,
            pair: `${config.currency}/${config.fiat}`,
            currency: config.currency,
            fiat: config.fiat,
            totalCrypto: 0.05432100,
            totalInvested: 2500.00,
            avgPrice: 46023,
            currentPrice: 48500,
            totalBuys: 25,
            profitPercent: 0.0538,
            profitFiat: 134.50,
            lastUpdate: new Date().toISOString(),
          };

          set({ stats: mockStats, isLoading: false });
        } catch (error) {
          set({
            error: error instanceof Error ? error.message : 'Failed to fetch stats',
            isLoading: false
          });
        }
      },

      setStats: (stats) => {
        set({ stats });
      },
    }),
    {
      name: 'accbot-storage',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        isConfigured: state.isConfigured,
        config: state.config,
      }),
    }
  )
);

// Exchange options
export const EXCHANGES = [
  { id: 'coinmate', name: 'Coinmate', description: 'Czech/European exchange', fiats: ['CZK', 'EUR'] },
  { id: 'binance', name: 'Binance', description: 'World\'s largest exchange', fiats: ['USDT', 'EUR', 'USDC'] },
  { id: 'kraken', name: 'Kraken', description: 'US-based trusted exchange', fiats: ['USD', 'EUR'] },
  { id: 'kucoin', name: 'KuCoin', description: 'Global exchange', fiats: ['USDT', 'USDC'] },
  { id: 'bitfinex', name: 'Bitfinex', description: 'Professional trading', fiats: ['USD', 'EUR'] },
  { id: 'huobi', name: 'HTX (Huobi)', description: 'Asian exchange', fiats: ['USDT'] },
  { id: 'coinbase', name: 'Coinbase', description: 'US-based beginner-friendly', fiats: ['USD', 'EUR'] },
] as const;

export const CURRENCIES = ['BTC', 'ETH', 'LTC', 'XRP', 'DASH'] as const;

export const HOUR_DIVIDERS = [
  { value: 1, label: 'Every hour' },
  { value: 2, label: 'Every 2 hours' },
  { value: 4, label: 'Every 4 hours' },
  { value: 6, label: 'Every 6 hours' },
  { value: 12, label: 'Every 12 hours' },
  { value: 24, label: 'Once a day' },
] as const;

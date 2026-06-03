const { createApp } = Vue;

createApp({
    data() {
        return {
            themeMode: 'auto',
            saving: false,
            resetting: false,
            settings: {
                cryptoMode: 'custom',
                cryptoSymbols: ['BTCUSDT', 'ETHUSDT', 'BNBUSDT'],
                fetchFrequency: 5,
                exchangeTypes: ['BINANCE'],
                pythonProjectPath: '',
                autoRefreshInterval: 30,
                themeMode: 'auto',
                enableNotifications: false,
                enableSoundAlert: false
            },
            systemInfo: {
                version: '1.0.0',
                uptime: '',
                totalSignals: 0,
                activeStrategies: 0
            },
            availableExchanges: ['BINANCE', 'OKEX', 'HUOBI', 'BYBIT', 'BITGET'],
            newCrypto: '',
            newExchange: '',
            minFetchFrequency: 5,
            showResetModal: false,
            resetConfirmText: ''
        };
    },
    mounted() {
        this.initTheme();
        this.loadSettings();
        this.loadSystemInfo();


        setInterval(() => {
            if (this.themeMode === 'auto') {
                this.updateThemeByTime();
            }
        }, 60000);
    },
    methods: {

        initTheme() {
            const savedTheme = localStorage.getItem('theme-mode');
            if (savedTheme && ['light', 'dark', 'auto'].includes(savedTheme)) {
                this.themeMode = savedTheme;
                this.settings.themeMode = savedTheme;
            } else {
                this.themeMode = 'auto';
                this.settings.themeMode = 'auto';
            }

            this.applyTheme();


            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
                if (this.themeMode === 'auto') {
                    this.applyTheme();
                }
            });
        },

        toggleTheme() {
            const modes = ['light', 'dark', 'auto'];
            const currentIndex = modes.indexOf(this.themeMode);
            this.themeMode = modes[(currentIndex + 1) % modes.length];
            this.settings.themeMode = this.themeMode;
            this.applyTheme();
            localStorage.setItem('theme-mode', this.themeMode);
        },

        applyTheme() {
            const root = document.documentElement;

            if (this.themeMode === 'auto') {
                const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                root.setAttribute('data-theme', prefersDark ? 'dark' : 'light');
            } else {
                root.setAttribute('data-theme', this.themeMode);
            }
        },

        applyThemeFromSettings() {
            this.themeMode = this.settings.themeMode;
            this.applyTheme();
            localStorage.setItem('theme-mode', this.themeMode);
        },

        updateThemeByTime() {
            const hour = new Date().getHours();
            const shouldBeDark = hour < 6 || hour >= 18;
            const root = document.documentElement;
            root.setAttribute('data-theme', shouldBeDark ? 'dark' : 'light');
        },

        getThemeIcon() {
            switch(this.themeMode) {
                case 'light': return 'fas fa-sun';
                case 'dark': return 'fas fa-moon';
                case 'auto': return 'fas fa-circle-half-stroke';
                default: return 'fas fa-circle-half-stroke';
            }
        },

        getThemeTooltip() {
            const titles = {
                'light': 'Light Theme',
                'dark': 'Dark Theme',
                'auto': 'Auto Theme'
            };
            return titles[this.themeMode] || 'Toggle Theme';
        },

        showToast(message, type = 'success') {
            const toast = document.getElementById('toast');
            const toastMessage = document.getElementById('toast-message');

            toastMessage.textContent = message;
            toast.className = `toast ${type}`;
            toast.classList.add('show');

            setTimeout(() => {
                toast.classList.remove('show');
            }, 3000);
        },


        async loadSettings() {
            try {
                const response = await fetch('/api/settings');
                if (response.ok) {
                    const data = await response.json();
                    if (data.success && data.data) {
                        Object.assign(this.settings, data.data);

                        if (data.data.themeMode) {
                            this.themeMode = data.data.themeMode;
                            this.applyTheme();
                        }
                    }
                }
            } catch (error) {
                console.error('Failed to load settings:', error);
                this.showToast('Failed to load settings: ' + error.message, 'error');
            }
        },

        async saveSettings() {
            this.saving = true;
            try {
                const response = await fetch('/api/settings', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(this.settings)
                });

                const result = await response.json();

                if (result.success) {
                    this.showToast('Settings saved successfully', 'success');
                } else {
                    this.showToast(result.message || 'Failed to save settings', 'error');
                }
            } catch (error) {
                console.error('Failed to save settings:', error);
                this.showToast('Failed to save settings: ' + error.message, 'error');
            } finally {
                this.saving = false;
            }
        },

        resetSettings() {
            this.settings = {
                cryptoMode: 'custom',
                cryptoSymbols: ['BTCUSDT', 'ETHUSDT', 'BNBUSDT'],
                fetchFrequency: 5,
                exchangeTypes: ['BINANCE'],
                pythonProjectPath: '',
                autoRefreshInterval: 30,
                themeMode: 'auto',
                enableNotifications: false,
                enableSoundAlert: false
            };
            this.themeMode = 'auto';
            this.applyTheme();
            this.showToast('Settings reset to defaults', 'info');
        },


        async loadSystemInfo() {
            try {
                const response = await fetch('/api/system/info');
                if (response.ok) {
                    const data = await response.json();
                    if (data.success && data.data) {
                        this.systemInfo = data.data;
                    }
                }
            } catch (error) {
                console.error('Failed to load system info:', error);
            }
        },


        addCrypto() {
            const crypto = this.newCrypto.trim().toUpperCase();
            if (crypto && !this.settings.cryptoSymbols.includes(crypto)) {
                this.settings.cryptoSymbols.push(crypto);
                this.newCrypto = '';
            } else if (this.settings.cryptoSymbols.includes(crypto)) {
                this.showToast('This crypto symbol already exists', 'warning');
            }
        },

        removeCrypto(index) {
            if (this.settings.cryptoSymbols.length > 1) {
                this.settings.cryptoSymbols.splice(index, 1);
            } else {
                this.showToast('At least one crypto symbol is required', 'warning');
            }
        },


        addExchange() {
            if (this.newExchange && !this.settings.exchangeTypes.includes(this.newExchange)) {
                this.settings.exchangeTypes.push(this.newExchange);
                this.newExchange = '';
            } else if (this.settings.exchangeTypes.includes(this.newExchange)) {
                this.showToast('This exchange already exists', 'warning');
            }
        },

        removeExchange(index) {
            if (this.settings.exchangeTypes.length > 1) {
                this.settings.exchangeTypes.splice(index, 1);
            } else {
                this.showToast('At least one exchange is required', 'warning');
            }
        },


        updateMinFetchFrequency() {
            this.minFetchFrequency = this.settings.cryptoMode === 'all' ? 30 : 5;
            if (this.settings.fetchFrequency < this.minFetchFrequency) {
                this.settings.fetchFrequency = this.minFetchFrequency;
            }
        },

        validateFetchFrequency() {
            if (this.settings.fetchFrequency < this.minFetchFrequency) {
                this.settings.fetchFrequency = this.minFetchFrequency;
                this.showToast(`Fetch frequency cannot be lower than ${this.minFetchFrequency} minutes`, 'warning');
            }
        },


        exportSettings() {
            const dataStr = JSON.stringify(this.settings, null, 2);
            const dataUri = 'data:application/json;charset=utf-8,'+ encodeURIComponent(dataStr);

            const exportFileDefaultName = `crypto-signal-settings-${new Date().toISOString().split('T')[0]}.json`;

            const linkElement = document.createElement('a');
            linkElement.setAttribute('href', dataUri);
            linkElement.setAttribute('download', exportFileDefaultName);
            linkElement.click();

            this.showToast('Settings exported successfully', 'success');
        },

        importSettings() {
            this.$refs.fileInput.click();
        },

        handleFileImport(event) {
            const file = event.target.files[0];
            if (!file) return;

            const reader = new FileReader();
            reader.onload = (e) => {
                try {
                    const importedSettings = JSON.parse(e.target.result);
                    Object.assign(this.settings, importedSettings);


                    if (importedSettings.themeMode) {
                        this.themeMode = importedSettings.themeMode;
                        this.applyTheme();
                    }

                    this.showToast('Settings imported successfully', 'success');
                } catch (error) {
                    this.showToast('Invalid settings file format', 'error');
                }

                event.target.value = '';
            };
            reader.readAsText(file);
        },


        async clearCache() {
            try {
                const response = await fetch('/api/system/clear-cache', {
                    method: 'POST'
                });

                const result = await response.json();
                if (result.success) {
                    this.showToast('Cache cleared successfully', 'success');
                } else {
                    this.showToast(result.message || 'Failed to clear cache', 'error');
                }
            } catch (error) {
                console.error('Failed to clear cache:', error);
                this.showToast('Failed to clear cache: ' + error.message, 'error');
            }
        },

        confirmReset() {
            this.showResetModal = true;
            this.resetConfirmText = '';
        },

        closeResetModal() {
            this.showResetModal = false;
            this.resetConfirmText = '';
        },

        async resetSystem() {
            if (this.resetConfirmText !== 'RESET') {
                this.showToast('Please enter the confirmation text correctly', 'warning');
                return;
            }

            this.resetting = true;
            try {
                const response = await fetch('/api/system/reset', {
                    method: 'POST'
                });

                const result = await response.json();
                if (result.success) {
                    this.showToast('System reset successful, page will refresh shortly', 'success');
                    setTimeout(() => {
                        window.location.href = '/';
                    }, 2000);
                } else {
                    this.showToast(result.message || 'System reset failed', 'error');
                }
            } catch (error) {
                console.error('System reset failed:', error);
                this.showToast('System reset failed: ' + error.message, 'error');
            } finally {
                this.resetting = false;
                this.closeResetModal();
            }
        }
    }
}).mount('#app');

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

        // 每分钟检查一次时间并更新主题
        setInterval(() => {
            if (this.themeMode === 'auto') {
                this.updateThemeByTime();
            }
        }, 60000);
    },
    methods: {
        // 主题相关方法
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
            
            // 监听系统主题变化
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
                'light': '浅色主题',
                'dark': '深色主题',
                'auto': '自动主题'
            };
            return titles[this.themeMode] || '主题切换';
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

        // 设置相关方法
        async loadSettings() {
            try {
                const response = await fetch('/api/settings');
                if (response.ok) {
                    const data = await response.json();
                    if (data.success && data.data) {
                        Object.assign(this.settings, data.data);
                        // 同步主题设置
                        if (data.data.themeMode) {
                            this.themeMode = data.data.themeMode;
                            this.applyTheme();
                        }
                    }
                }
            } catch (error) {
                console.error('加载设置失败:', error);
                this.showToast('加载设置失败: ' + error.message, 'error');
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
                    this.showToast('设置保存成功', 'success');
                } else {
                    this.showToast(result.message || '保存设置失败', 'error');
                }
            } catch (error) {
                console.error('保存设置失败:', error);
                this.showToast('保存设置失败: ' + error.message, 'error');
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
            this.showToast('设置已恢复默认', 'info');
        },

        // 系统信息相关方法
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
                console.error('加载系统信息失败:', error);
            }
        },

        // 加密货币管理
        addCrypto() {
            const crypto = this.newCrypto.trim().toUpperCase();
            if (crypto && !this.settings.cryptoSymbols.includes(crypto)) {
                this.settings.cryptoSymbols.push(crypto);
                this.newCrypto = '';
            } else if (this.settings.cryptoSymbols.includes(crypto)) {
                this.showToast('该加密货币代号已存在', 'warning');
            }
        },

        removeCrypto(index) {
            if (this.settings.cryptoSymbols.length > 1) {
                this.settings.cryptoSymbols.splice(index, 1);
            } else {
                this.showToast('至少需要保留一个加密货币', 'warning');
            }
        },

        // 交易所管理
        addExchange() {
            if (this.newExchange && !this.settings.exchangeTypes.includes(this.newExchange)) {
                this.settings.exchangeTypes.push(this.newExchange);
                this.newExchange = '';
            } else if (this.settings.exchangeTypes.includes(this.newExchange)) {
                this.showToast('该交易所已存在', 'warning');
            }
        },

        removeExchange(index) {
            if (this.settings.exchangeTypes.length > 1) {
                this.settings.exchangeTypes.splice(index, 1);
            } else {
                this.showToast('至少需要保留一个交易所', 'warning');
            }
        },

        // 频率验证
        updateMinFetchFrequency() {
            this.minFetchFrequency = this.settings.cryptoMode === 'all' ? 30 : 5;
            if (this.settings.fetchFrequency < this.minFetchFrequency) {
                this.settings.fetchFrequency = this.minFetchFrequency;
            }
        },

        validateFetchFrequency() {
            if (this.settings.fetchFrequency < this.minFetchFrequency) {
                this.settings.fetchFrequency = this.minFetchFrequency;
                this.showToast(`抓取频率不能低于 ${this.minFetchFrequency} 分钟`, 'warning');
            }
        },

        // 配置导入导出
        exportSettings() {
            const dataStr = JSON.stringify(this.settings, null, 2);
            const dataUri = 'data:application/json;charset=utf-8,'+ encodeURIComponent(dataStr);
            
            const exportFileDefaultName = `crypto-signal-settings-${new Date().toISOString().split('T')[0]}.json`;
            
            const linkElement = document.createElement('a');
            linkElement.setAttribute('href', dataUri);
            linkElement.setAttribute('download', exportFileDefaultName);
            linkElement.click();
            
            this.showToast('配置导出成功', 'success');
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
                    
                    // 同步主题设置
                    if (importedSettings.themeMode) {
                        this.themeMode = importedSettings.themeMode;
                        this.applyTheme();
                    }
                    
                    this.showToast('配置导入成功', 'success');
                } catch (error) {
                    this.showToast('配置文件格式错误', 'error');
                }
                // 清空文件输入
                event.target.value = '';
            };
            reader.readAsText(file);
        },

        // 系统操作
        async clearCache() {
            try {
                const response = await fetch('/api/system/clear-cache', {
                    method: 'POST'
                });
                
                const result = await response.json();
                if (result.success) {
                    this.showToast('缓存清除成功', 'success');
                } else {
                    this.showToast(result.message || '缓存清除失败', 'error');
                }
            } catch (error) {
                console.error('清除缓存失败:', error);
                this.showToast('清除缓存失败: ' + error.message, 'error');
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
                this.showToast('请正确输入确认文本', 'warning');
                return;
            }
            
            this.resetting = true;
            try {
                const response = await fetch('/api/system/reset', {
                    method: 'POST'
                });
                
                const result = await response.json();
                if (result.success) {
                    this.showToast('系统重置成功，页面即将刷新', 'success');
                    setTimeout(() => {
                        window.location.href = '/';
                    }, 2000);
                } else {
                    this.showToast(result.message || '系统重置失败', 'error');
                }
            } catch (error) {
                console.error('系统重置失败:', error);
                this.showToast('系统重置失败: ' + error.message, 'error');
            } finally {
                this.resetting = false;
                this.closeResetModal();
            }
        }
    }
}).mount('#app');
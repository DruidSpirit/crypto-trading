console.log('=== main.js loading ===');
console.log('Vue:', Vue);

const { createApp } = Vue;
const ITEMS_PER_PAGE = 10;
const MAX_VISIBLE_PAGES = 5;

console.log('=== Creating Vue app ===');

createApp({
    data() {
        return {
            isDarkTheme: true,
            themeMode: 'auto', // auto, dark, light
            loading: false,
            showSettings: false,
            showDetailsModal: false,
            currentTab: 'dashboard', // dashboard, signals, strategies, backtest, settings
            settings: {
                cryptoMode: 'custom',
                cryptoSymbols: [],
                exchangeTypes: [],
                fetchFrequency: 15,
                proxies: []
            },
            originalSettings: null,
            filter: {
                search: '',
                signalType: '',
                strategy: '',
                exchange: '',
                startDate: '',
                endDate: ''
            },
            signals: [],
            dashboardSignals: [],
            totalPages: 1,
            currentPage: 1,
            maxVisiblePages: MAX_VISIBLE_PAGES,
            selectedSignal: null,
            selectOptions: {
                exchangeTypes: [],
                strategyNames: [],
                defaultCryptoCoinSymbols: []
            },
            newCrypto: '',
            newExchange: '',
            minFetchFrequency: 5,
            dashboardStats: {
                totalSignals: 0,
                buySignals: 0,
                sellSignals: 0,
                activePairs: 0
            },
            signalChart: null,
            filterTimeout: null,
            filterToggleTimeout: null,
            mobileFilterExpanded: false,
            strategies: [],
            showUploadStrategyModal: false,
            showDeleteConfirmModal: false,
            selectedFile: null,
            strategyDescription: '',
            uploading: false,
            deleting: false,
            strategyToDelete: null,
            backtest: {
                strategyName: '',
                symbol: 'BTCUSDT',
                startDate: '',
                endDate: '',
                initialBalance: 10000,
                running: false,
                results: null
            }
        };
    },
    computed: {
        visiblePages() {
            const half = Math.floor(this.maxVisiblePages / 2);
            let start = Math.max(1, this.currentPage - half);
            let end = Math.min(this.totalPages, start + this.maxVisiblePages - 1);
            if (end - start + 1 < this.maxVisiblePages) {
                start = Math.max(1, end - this.maxVisiblePages + 1);
            }
            return Array.from({ length: end - start + 1 }, (_, i) => start + i);
        },
        availableExchanges() {
            return this.selectOptions.exchangeTypes.filter(
                exchange => !this.settings.exchangeTypes.includes(exchange)
            );
        },
        hasActiveFilters() {
            return this.filter.search ||
                   this.filter.signalType ||
                   this.filter.strategy ||
                   this.filter.exchange ||
                   this.filter.startDate ||
                   this.filter.endDate;
        },
        activeFilterCount() {
            let count = 0;
            if (this.filter.search) count++;
            if (this.filter.signalType) count++;
            if (this.filter.strategy) count++;
            if (this.filter.exchange) count++;
            if (this.filter.startDate) count++;
            if (this.filter.endDate) count++;
            return count;
        }
    },
    mounted() {
        console.log('=== Vue app mounted ===');
        console.log('Initializing app components...');
        this.loadSelectOptions();
        this.fetchSignals();
        this.loadDashboardStats();
        this.loadDashboardSignals();
        this.initChart();
        this.initTheme();
        this.loadSettings();
        console.log('Calling loadStrategies...');
        this.loadStrategies();
        this.initBacktestDates();
        setInterval(() => {
            if (this.themeMode === 'auto') {
                this.updateThemeByTime();
            }
        }, 60000);
    },
    methods: {
        toggleTheme() {
            if (this.themeMode === 'auto') {
                this.themeMode = 'dark';
                this.isDarkTheme = true;
            } else if (this.themeMode === 'dark') {
                this.themeMode = 'light';
                this.isDarkTheme = false;
            } else {
                this.themeMode = 'auto';
                this.updateThemeByTime();
            }
            this.saveThemePreference();
            this.applyTheme();
        },
        initTheme() {
            const savedMode = localStorage.getItem('themeMode') || 'auto';
            this.themeMode = savedMode;
            if (this.themeMode === 'auto') {
                this.updateThemeByTime();
            } else {
                this.isDarkTheme = this.themeMode === 'dark';
            }
            this.applyTheme();
        },
        updateThemeByTime() {
            const now = new Date();
            const hour = now.getHours();
            this.isDarkTheme = hour < 6 || hour >= 18;
        },
        applyTheme() {
            document.body.classList.toggle('theme-light', !this.isDarkTheme);
        },
        saveThemePreference() {
            localStorage.setItem('themeMode', this.themeMode);
        },
        getThemeIcon() {
            if (this.themeMode === 'auto') {
                return 'fas fa-clock';
            } else if (this.isDarkTheme) {
                return 'fas fa-sun';
            } else {
                return 'fas fa-moon';
            }
        },
        getThemeTooltip() {
            if (this.themeMode === 'auto') {
                return 'Auto Theme (based on time)';
            } else if (this.isDarkTheme) {
                return 'Dark Theme';
            } else {
                return 'Light Theme';
            }
        },
        switchTab(tab) {
            this.currentTab = tab;
            if (tab === 'settings') {
                this.loadSettings();
            }
            if (tab === 'signals') {
                this.resetFilter();
            }
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
        debounceFilter() {
            if (this.filterTimeout) {
                clearTimeout(this.filterTimeout);
            }
            this.filterTimeout = setTimeout(() => {
                this.applyFilter();
            }, 500);
        },
        loadSettings() {
            ApiService.getSettings()
                .then(response => {
                    const settings = response.data || {};
                    this.settings = {
                        cryptoMode: settings.cryptoMode || 'custom',
                        cryptoSymbols: settings.cryptoSymbols || this.selectOptions.defaultCryptoCoinSymbols.slice(0, 10),
                        exchangeTypes: settings.exchangeTypes || ['GATE_IO'],
                        fetchFrequency: settings.fetchFrequency || 15,
                        proxies: settings.proxies || []
                    };
                    this.updateMinFetchFrequency();
                    this.originalSettings = JSON.parse(JSON.stringify(this.settings));
                })
                .catch(error => {
                    console.error('Failed to load settings:', error);
                    this.showToast('Failed to load settings', 'error');
                });
        },
        saveSettings() {
            if (this.settings.fetchFrequency < this.minFetchFrequency) {
                this.settings.fetchFrequency = this.minFetchFrequency;
                this.showToast(`Frequency adjusted to minimum ${this.minFetchFrequency} minutes`, 'warning');
            }
            ApiService.saveSettings(this.settings)
                .then(() => {
                    this.showToast('Settings saved successfully!');
                    this.originalSettings = JSON.parse(JSON.stringify(this.settings));
                })
                .catch(error => {
                    console.error('Failed to save settings:', error);
                    this.showToast('Failed to save settings', 'error');
                });
        },
        addCrypto() {
            if (this.newCrypto && this.newCrypto.trim()) {
                const crypto = this.newCrypto.trim().toUpperCase();
                if (!this.settings.cryptoSymbols.includes(crypto)) {
                    this.settings.cryptoSymbols.push(crypto);
                    this.newCrypto = '';
                    this.updateMinFetchFrequency();
                }
            }
        },
        removeCrypto(index) {
            this.settings.cryptoSymbols.splice(index, 1);
            this.updateMinFetchFrequency();
        },
        addExchange() {
            if (this.newExchange && !this.settings.exchangeTypes.includes(this.newExchange)) {
                this.settings.exchangeTypes.push(this.newExchange);
                this.newExchange = '';
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
            const baseTimePerCoin = 20;
            let coinCount = this.settings.cryptoMode === 'custom'
                ? this.settings.cryptoSymbols.length
                : 2000;
            const proxyCount = Math.max(1, this.settings.proxies.length);
            let totalMinutes = Math.ceil(coinCount * baseTimePerCoin / proxyCount / 60);
            this.minFetchFrequency = Math.max(totalMinutes, 5);
        },
        validateFetchFrequency() {
            if (this.settings.fetchFrequency < this.minFetchFrequency) {
                this.settings.fetchFrequency = this.minFetchFrequency;
            }
        },
        loadSelectOptions() {
            ApiService.getSelectOptions()
                .then(response => {
                    this.selectOptions = response.data;
                })
                .catch(error => console.error('Failed to load options:', error));
        },
        loadDashboardStats() {
            ApiService.getDashboardStats()
                .then(response => {
                    this.dashboardStats = response.data;
                })
                .catch(error => {
                    console.error('Failed to get stats:', error);
                    this.dashboardStats = {
                        totalSignals: this.signals.length || 156,
                        buySignals: Math.floor((this.signals.length || 156) * 0.6),
                        sellSignals: Math.floor((this.signals.length || 156) * 0.4),
                        activePairs: 45
                    };
                });
        },
        loadDashboardSignals() {
            ApiService.getDashboardLatestSignals(5)
                .then(response => {
                    this.dashboardSignals = response.data;
                })
                .catch(error => {
                    console.error('Failed to get dashboard signals:', error);
                    this.dashboardSignals = this.signals.slice(0, 5);
                });
        },
        initChart() {
            ApiService.getDashboardChart()
                .then(response => {
                    const chartData = response.data.chartData;
                    this.createChart(chartData);
                })
                .catch(error => {
                    console.error('Failed to get chart data:', error);
                    const emptyData = {
                        labels: ['Sep', 'Oct', 'Nov', 'Dec', 'Jan', 'Feb'],
                        buyData: [0, 0, 0, 0, 0, 0],
                        sellData: [0, 0, 0, 0, 0, 0]
                    };
                    this.createChart(emptyData);
                });
        },
        createChart(chartData) {
            const ctx = document.getElementById('signalChart').getContext('2d');
            this.signalChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: chartData.labels,
                    datasets: [{
                        label: 'Buy Signals',
                        data: chartData.buyData,
                        borderColor: 'rgb(16, 185, 129)',
                        backgroundColor: 'rgba(16, 185, 129, 0.1)',
                        tension: 0.4,
                        fill: true
                    }, {
                        label: 'Sell Signals',
                        data: chartData.sellData,
                        borderColor: 'rgb(239, 68, 68)',
                        backgroundColor: 'rgba(239, 68, 68, 0.1)',
                        tension: 0.4,
                        fill: true
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            labels: {
                                color: getComputedStyle(document.documentElement).getPropertyValue('--text-secondary'),
                                usePointStyle: true,
                                padding: 20
                            }
                        },
                        tooltip: {
                            backgroundColor: 'rgba(0, 0, 0, 0.8)',
                            titleColor: '#fff',
                            bodyColor: '#fff',
                            cornerRadius: 8
                        }
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            ticks: {
                                color: getComputedStyle(document.documentElement).getPropertyValue('--text-secondary')
                            },
                            grid: {
                                color: getComputedStyle(document.documentElement).getPropertyValue('--border-color')
                            }
                        },
                        x: {
                            ticks: {
                                color: getComputedStyle(document.documentElement).getPropertyValue('--text-secondary')
                            },
                            grid: {
                                color: getComputedStyle(document.documentElement).getPropertyValue('--border-color')
                            }
                        }
                    },
                    elements: {
                        point: {
                            radius: 4,
                            hoverRadius: 8
                        }
                    }
                }
            });
        },
        fetchSignals() {
            this.loading = true;
            const payload = {
                search: this.filter.search,
                signalType: this.filter.signalType,
                strategy: this.filter.strategy,
                exchange: this.filter.exchange,
                startDate: this.filter.startDate,
                endDate: this.filter.endDate,
                page: this.currentPage,
                size: ITEMS_PER_PAGE
            };
            ApiService.getSignalsList(payload)
                .then(response => {
                    this.signals = response.data.content;
                    this.totalPages = response.data.totalPages;
                })
                .catch(error => {
                    console.error('Failed to get signals:', error);
                    this.showToast('Failed to get signals', 'error');
                })
                .finally(() => {
                    this.loading = false;
                });
        },
        refreshSignals() {
            if (this.currentTab === 'dashboard') {
                this.loadDashboardSignals();
                this.loadDashboardStats();
                this.showToast('Dashboard data refreshed');
            } else {
                this.fetchSignals();
                this.showToast('Data refreshed');
            }
        },
        applyFilter() {
            this.currentPage = 1;
            this.selectedSignal = null;
            this.fetchSignals();
        },
        resetFilter() {
            this.filter = {
                search: '',
                signalType: '',
                strategy: '',
                exchange: '',
                startDate: '',
                endDate: ''
            };
            this.currentPage = 1;
            this.selectedSignal = null;
            this.fetchSignals();
        },
        changePage(page) {
            this.currentPage = page;
            this.selectedSignal = null;
            this.fetchSignals();
        },
        prevPage() {
            if (this.currentPage > 1) {
                this.currentPage--;
                this.fetchSignals();
            }
        },
        nextPage() {
            if (this.currentPage < this.totalPages) {
                this.currentPage++;
                this.fetchSignals();
            }
        },
        showDetails(signal) {
            this.selectedSignal = signal;
            this.showDetailsModal = true;
        },
        closeDetailsModal() {
            this.showDetailsModal = false;
            this.selectedSignal = null;
        },
        formattedSignalTime(time) {
            const date = new Date(time);
            return date.toLocaleString('en-US', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        },
        formatProfitLossRatio(ratio) {
            if (ratio === null || ratio === undefined) {
                return 'Not set';
            }
            return `${parseFloat(ratio).toFixed(2)}:1`;
        },
        async loadStrategies() {
            console.log('loadStrategies executing...');
            this.loading = true;
            try {
                console.log('Sending API request to: /api/strategies');
                const response = await fetch('/api/strategies');
                console.log('API response status:', response.status);
                const data = await response.json();
                console.log('API response data:', data);
                if (Array.isArray(data)) {
                    this.strategies = data;
                    console.log('Strategies loaded:', this.strategies.length, 'items');
                } else {
                    console.error('Invalid response format:', data);
                    this.showToast('Failed to load strategies: Invalid response format', 'error');
                }
            } catch (error) {
                console.error('Failed to load strategies:', error);
                this.showToast('Failed to load strategies: ' + error.message, 'error');
            } finally {
                this.loading = false;
                console.log('loadStrategies completed');
            }
        },
        showUploadModal() {
            this.showUploadStrategyModal = true;
            this.selectedFile = null;
            this.strategyDescription = '';
        },
        closeUploadModal() {
            this.showUploadStrategyModal = false;
            this.selectedFile = null;
            this.strategyDescription = '';
            if (this.$refs.strategyFileInput) {
                this.$refs.strategyFileInput.value = '';
            }
        },
        onFileSelected(event) {
            const file = event.target.files[0];
            if (file) {
                if (file.size > 10 * 1024 * 1024) { // 10MB
                    this.showToast('File size cannot exceed 10MB', 'error');
                    event.target.value = '';
                    return;
                }
                if (!file.name.endsWith('.py')) {
                    this.showToast('Only .py format files are supported', 'error');
                    event.target.value = '';
                    return;
                }
                this.selectedFile = file;
            }
        },
        async uploadStrategy() {
            if (!this.selectedFile) {
                this.showToast('Please select a file', 'error');
                return;
            }
            this.uploading = true;
            const formData = new FormData();
            formData.append('file', this.selectedFile);
            if (this.strategyDescription) {
                formData.append('description', this.strategyDescription);
            }
            try {
                const response = await fetch('/api/strategies/upload', {
                    method: 'POST',
                    body: formData
                });
                const result = await response.json();
                if (result.success) {
                    this.showToast('Strategy uploaded successfully', 'success');
                    this.closeUploadModal();
                    this.loadStrategies();
                } else {
                    this.showToast(result.message || 'Upload failed', 'error');
                }
            } catch (error) {
                console.error('Failed to upload strategy:', error);
                this.showToast('Failed to upload strategy: ' + error.message, 'error');
            } finally {
                this.uploading = false;
            }
        },
        async downloadStrategy(strategy) {
            try {
                const response = await fetch(`/api/strategies/${strategy.id}/download`);
                if (response.ok) {
                    const blob = await response.blob();
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = strategy.filename;
                    document.body.appendChild(a);
                    a.click();
                    window.URL.revokeObjectURL(url);
                    document.body.removeChild(a);
                    this.showToast('Download successful', 'success');
                } else {
                    this.showToast('Download failed', 'error');
                }
            } catch (error) {
                console.error('Failed to download strategy:', error);
                this.showToast('Failed to download strategy: ' + error.message, 'error');
            }
        },
        async hotReloadStrategy(strategy) {
            try {
                const response = await fetch(`/api/strategies/${strategy.id}/reload`, {
                    method: 'POST'
                });
                const result = await response.json();
                if (result.success) {
                    this.showToast('Hot reload successful', 'success');
                    this.loadStrategies();
                } else {
                    this.showToast(result.message || 'Hot reload failed', 'error');
                }
            } catch (error) {
                console.error('Hot reload failed:', error);
                this.showToast('Hot reload failed: ' + error.message, 'error');
            }
        },
        deleteStrategy(strategy) {
            this.strategyToDelete = strategy;
            this.showDeleteConfirmModal = true;
        },
        closeDeleteConfirmModal() {
            this.showDeleteConfirmModal = false;
            this.strategyToDelete = null;
        },
        async confirmDeleteStrategy() {
            if (!this.strategyToDelete) return;
            this.deleting = true;
            try {
                const response = await fetch(`/api/strategies/${this.strategyToDelete.id}`, {
                    method: 'DELETE'
                });
                const result = await response.json();
                if (result.success) {
                    this.showToast('Strategy deleted successfully', 'success');
                    this.closeDeleteConfirmModal();
                    this.loadStrategies();
                } else {
                    this.showToast(result.message || 'Deletion failed', 'error');
                }
            } catch (error) {
                console.error('Failed to delete strategy:', error);
                this.showToast('Failed to delete strategy: ' + error.message, 'error');
            } finally {
                this.deleting = false;
            }
        },
        formattedTime(time) {
            const date = new Date(time);
            return date.toLocaleString('en-US', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        },
        formatFileSize(bytes) {
            if (bytes === 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        },
        getStatusIcon(status) {
            const icons = {
                'ACTIVE': 'fas fa-check-circle',
                'INACTIVE': 'fas fa-pause-circle',
                'UPDATING': 'fas fa-sync-alt fa-spin',
                'ERROR': 'fas fa-exclamation-circle'
            };
            return icons[status] || 'fas fa-question-circle';
        },
        getStatusText(status) {
            const texts = {
                'ACTIVE': 'Active',
                'INACTIVE': 'Inactive',
                'UPDATING': 'Updating',
                'ERROR': 'Error'
            };
            return texts[status] || 'Unknown';
        },
        initBacktestDates() {
            const today = new Date();
            const oneYearAgo = new Date(today.getFullYear() - 1, today.getMonth(), today.getDate());
            this.backtest.startDate = oneYearAgo.toISOString().split('T')[0];
            this.backtest.endDate = today.toISOString().split('T')[0];
        },
        async runBacktest() {
            if (!this.backtest.strategyName || !this.backtest.symbol ||
                !this.backtest.startDate || !this.backtest.endDate) {
                this.showToast('Please fill in all required fields', 'warning');
                return;
            }
            this.backtest.running = true;
            this.backtest.results = null;
            try {
                const request = {
                    strategy_name: this.backtest.strategyName,
                    symbol: this.backtest.symbol,
                    start_date: this.backtest.startDate,
                    end_date: this.backtest.endDate,
                    initial_balance: this.backtest.initialBalance
                };
                const response = await fetch('/api/backtest/run', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(request)
                });
                const result = await response.json();
                if (result.success && result.data) {
                    this.backtest.results = result.data;
                    this.showToast('Backtest complete', 'success');
                } else {
                    this.showToast(result.message || 'Backtest failed', 'error');
                }
            } catch (error) {
                console.error('Backtest request failed:', error);
                this.showToast('Backtest request failed: ' + error.message, 'error');
            } finally {
                this.backtest.running = false;
            }
        },
        resetBacktestForm() {
            this.backtest.strategyName = '';
            this.backtest.symbol = 'BTCUSDT';
            this.backtest.initialBalance = 10000;
            this.backtest.results = null;
            this.initBacktestDates();
        },
        toggleMobileFilter(event) {
            if (event) {
                event.preventDefault();
                event.stopPropagation();
            }
            if (this.filterToggleTimeout) {
                return;
            }
            this.filterToggleTimeout = setTimeout(() => {
                this.filterToggleTimeout = null;
            }, 100);
            this.mobileFilterExpanded = !this.mobileFilterExpanded;
        }
    }
}).mount('#app');

console.log('=== Vue app mounted complete ===');

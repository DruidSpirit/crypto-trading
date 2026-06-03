const { createApp } = Vue;

createApp({
    data() {
        return {
            themeMode: 'auto',
            loading: false,
            strategies: [],
            showUploadStrategyModal: false,
            showDeleteConfirmModal: false,
            selectedFile: null,
            strategyDescription: '',
            uploading: false,
            deleting: false,
            strategyToDelete: null
        };
    },
    mounted() {
        this.initTheme();
        this.loadStrategies();


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
            } else {
                this.themeMode = 'auto';
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


        async loadStrategies() {
            console.log(11)
            this.loading = true;
            try {
                const response = await fetch('/api/strategy/files');
                const data = await response.json();

                if (data.success) {
                    this.strategies = data.data || [];
                } else {
                    this.showToast(data.message || 'Failed to load strategies', 'error');
                }
            } catch (error) {
                console.error('Failed to load strategies:', error);
                this.showToast('Failed to load strategies: ' + error.message, 'error');
            } finally {
                this.loading = false;
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
                const response = await fetch('/api/strategy/upload', {
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
                const response = await fetch(`/api/strategy/download/${strategy.id}`);
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
                const response = await fetch(`/api/strategy/hot-reload/${strategy.id}`, {
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
                const response = await fetch(`/api/strategy/delete/${this.strategyToDelete.id}`, {
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

        formatFileSize(bytes) {
            if (bytes === 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
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
        }
    }
}).mount('#app');

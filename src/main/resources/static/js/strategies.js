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
            } else {
                this.themeMode = 'auto';
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

        // 策略管理方法
        async loadStrategies() {
            this.loading = true;
            try {
                const response = await fetch('/api/strategy/files');
                const data = await response.json();
                
                if (data.success) {
                    this.strategies = data.data || [];
                } else {
                    this.showToast(data.message || '加载策略失败', 'error');
                }
            } catch (error) {
                console.error('加载策略失败:', error);
                this.showToast('加载策略失败: ' + error.message, 'error');
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
                    this.showToast('文件大小不能超过10MB', 'error');
                    event.target.value = '';
                    return;
                }
                if (!file.name.endsWith('.py')) {
                    this.showToast('只支持.py格式的文件', 'error');
                    event.target.value = '';
                    return;
                }
                this.selectedFile = file;
            }
        },

        async uploadStrategy() {
            if (!this.selectedFile) {
                this.showToast('请选择文件', 'error');
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
                    this.showToast('策略上传成功', 'success');
                    this.closeUploadModal();
                    this.loadStrategies();
                } else {
                    this.showToast(result.message || '上传失败', 'error');
                }
            } catch (error) {
                console.error('上传策略失败:', error);
                this.showToast('上传策略失败: ' + error.message, 'error');
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
                    this.showToast('下载成功', 'success');
                } else {
                    this.showToast('下载失败', 'error');
                }
            } catch (error) {
                console.error('下载策略失败:', error);
                this.showToast('下载策略失败: ' + error.message, 'error');
            }
        },

        async hotReloadStrategy(strategy) {
            try {
                const response = await fetch(`/api/strategy/hot-reload/${strategy.id}`, {
                    method: 'POST'
                });
                const result = await response.json();
                
                if (result.success) {
                    this.showToast('热更新成功', 'success');
                    this.loadStrategies();
                } else {
                    this.showToast(result.message || '热更新失败', 'error');
                }
            } catch (error) {
                console.error('热更新失败:', error);
                this.showToast('热更新失败: ' + error.message, 'error');
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
                    this.showToast('策略删除成功', 'success');
                    this.closeDeleteConfirmModal();
                    this.loadStrategies();
                } else {
                    this.showToast(result.message || '删除失败', 'error');
                }
            } catch (error) {
                console.error('删除策略失败:', error);
                this.showToast('删除策略失败: ' + error.message, 'error');
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
            return date.toLocaleString('zh-CN', {
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
                'ACTIVE': '已激活',
                'INACTIVE': '未激活',
                'UPDATING': '更新中',
                'ERROR': '错误'
            };
            return texts[status] || '未知';
        }
    }
}).mount('#app');
// Strategies组件
const Strategies = Vue.defineComponent({
    name: 'Strategies',
    template: `
        <div class="tab-content">
            <div class="section">
                <div class="section-header">
                    <h2>
                        <i class="fas fa-code"></i>
                        策略管理
                    </h2>
                    <div class="section-actions">
                        <button class="btn btn-primary" @click="showUploadModal">
                            <i class="fas fa-plus"></i>
                            上传策略
                        </button>
                        <button class="btn btn-secondary" @click="loadStrategies">
                            <i class="fas fa-sync-alt" :class="{ 'fa-spin': loading }"></i>
                            刷新
                        </button>
                    </div>
                </div>

                <!-- Desktop Table -->
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>策略名称</th>
                                <th>文件名</th>
                                <th>状态</th>
                                <th>文件大小</th>
                                <th>描述</th>
                                <th>创建时间</th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="strategy in strategies" :key="strategy.id">
                                <td>
                                    <span class="strategy-name">{{ strategy.displayName || strategy.description || strategy.originalFilename.replace('.py', '') }}</span>
                                </td>
                                <td>{{ strategy.originalFilename }}</td>
                                <td>
                                    <span :class="['status-badge', strategy.status.toLowerCase()]">
                                        <i :class="getStatusIcon(strategy.status)"></i>
                                        {{ getStatusText(strategy.status) }}
                                    </span>
                                </td>
                                <td>{{ formatFileSize(strategy.fileSize) }}</td>
                                <td>{{ strategy.description || '无描述' }}</td>
                                <td>{{ formattedSignalTime(strategy.createdAt) }}</td>
                                <td>
                                    <div class="action-buttons">
                                        <button class="btn btn-sm btn-outline" @click="downloadStrategy(strategy)" title="下载">
                                            <i class="fas fa-download"></i>
                                        </button>
                                        <button class="btn btn-sm btn-outline" @click="hotReloadStrategy(strategy)" title="热更新">
                                            <i class="fas fa-sync-alt"></i>
                                        </button>
                                        <button class="btn btn-sm btn-danger" @click="deleteStrategy(strategy)" title="删除">
                                            <i class="fas fa-trash"></i>
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                <!-- Mobile Cards -->
                <div class="mobile-strategy-cards">
                    <div v-for="strategy in strategies" :key="strategy.id" class="strategy-card">
                        <div class="strategy-card-header">
                            <span class="strategy-name">{{ strategy.displayName || strategy.description || strategy.originalFilename.replace('.py', '') }}</span>
                            <span :class="['status-badge', strategy.status.toLowerCase()]">
                                <i :class="getStatusIcon(strategy.status)"></i>
                                {{ getStatusText(strategy.status) }}
                            </span>
                        </div>
                        <div class="strategy-card-body">
                            <div class="strategy-info">
                                <div class="info-item">
                                    <span class="label">文件名:</span>
                                    <span class="value">{{ strategy.originalFilename }}</span>
                                </div>
                                <div class="info-item">
                                    <span class="label">大小:</span>
                                    <span class="value">{{ formatFileSize(strategy.fileSize) }}</span>
                                </div>
                                <div class="info-item">
                                    <span class="label">创建时间:</span>
                                    <span class="value">{{ formattedSignalTime(strategy.createdAt) }}</span>
                                </div>
                                <div class="info-item" v-if="strategy.description">
                                    <span class="label">描述:</span>
                                    <span class="value">{{ strategy.description }}</span>
                                </div>
                            </div>
                            <div class="strategy-actions">
                                <button class="btn btn-sm btn-outline" @click="downloadStrategy(strategy)">
                                    <i class="fas fa-download"></i>
                                    下载
                                </button>
                                <button class="btn btn-sm btn-outline" @click="hotReloadStrategy(strategy)">
                                    <i class="fas fa-sync-alt"></i>
                                    热更新
                                </button>
                                <button class="btn btn-sm btn-danger" @click="deleteStrategy(strategy)">
                                    <i class="fas fa-trash"></i>
                                    删除
                                </button>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Upload Modal -->
                <div v-if="showUploadStrategyModal" class="modal" style="display: block;">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h3>上传策略文件</h3>
                            <button class="modal-close" @click="closeUploadModal">&times;</button>
                        </div>
                        <div class="modal-body">
                            <div class="form-group">
                                <label for="strategyFile">选择Python策略文件 (.py)</label>
                                <input type="file" 
                                       id="strategyFile" 
                                       ref="strategyFileInput"
                                       accept=".py" 
                                       @change="onFileSelected" 
                                       class="form-control">
                                <small class="form-help">支持.py格式文件，最大10MB</small>
                            </div>
                            <div class="form-group">
                                <label for="strategyDescription">策略描述（可选）</label>
                                <textarea id="strategyDescription" 
                                          v-model="strategyDescription" 
                                          class="form-control" 
                                          rows="3" 
                                          placeholder="请输入策略的简要描述..."></textarea>
                            </div>
                            <div v-if="selectedFile" class="file-info">
                                <i class="fas fa-file-code"></i>
                                <span>{{ selectedFile.name }}</span>
                                <span class="file-size">({{ formatFileSize(selectedFile.size) }})</span>
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button class="btn btn-secondary" @click="closeUploadModal">取消</button>
                            <button class="btn btn-primary" @click="uploadStrategy" :disabled="!selectedFile || uploading">
                                <i class="fas fa-upload" :class="{ 'fa-spin': uploading }"></i>
                                {{ uploading ? '上传中...' : '上传' }}
                            </button>
                        </div>
                    </div>
                </div>

                <!-- Delete Confirmation Modal -->
                <div v-if="showDeleteConfirmModal" class="modal" style="display: block;">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h3>确认删除</h3>
                            <button class="modal-close" @click="closeDeleteConfirmModal">&times;</button>
                        </div>
                        <div class="modal-body">
                            <p>确定要删除策略 <strong>{{ strategyToDelete?.originalFilename }}</strong> 吗？</p>
                            <p class="text-danger">此操作不可撤销。</p>
                        </div>
                        <div class="modal-footer">
                            <button class="btn btn-secondary" @click="closeDeleteConfirmModal">取消</button>
                            <button class="btn btn-danger" @click="confirmDeleteStrategy" :disabled="deleting">
                                <i class="fas fa-trash" :class="{ 'fa-spin': deleting }"></i>
                                {{ deleting ? '删除中...' : '确认删除' }}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `,
    inject: [
        'strategies', 'loading', 'showUploadStrategyModal', 'showDeleteConfirmModal', 
        'selectedFile', 'strategyDescription', 'uploading', 'deleting', 'strategyToDelete',
        'loadStrategies', 'showUploadModal', 'closeUploadModal', 'onFileSelected', 
        'uploadStrategy', 'downloadStrategy', 'hotReloadStrategy', 'deleteStrategy',
        'closeDeleteConfirmModal', 'confirmDeleteStrategy', 'formatFileSize', 
        'getStatusIcon', 'getStatusText', 'formattedSignalTime'
    ]
});
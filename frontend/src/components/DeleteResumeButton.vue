<script setup lang="ts">
import {ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {Delete} from '@element-plus/icons-vue'
import type {Summary} from '../types'
import {api,showError} from '../api'
const props=defineProps<{resume:Summary;disabled?:boolean;compact?:boolean}>()
const emit=defineEmits<{deleted:[]}>(),busy=ref(false)
async function remove(){try{await ElMessageBox.confirm(`删除“${props.resume.originalName}”？删除后简历、历史版本、项目与原文件将无法通过平台访问。原文件和历史数据暂时保留，不会物理清理，也不影响已保存的训练快照。`,'删除简历',{confirmButtonText:'确认删除',cancelButtonText:'保留简历',type:'warning',closeOnClickModal:false});busy.value=true;await api.deleteResume(props.resume.id,props.resume.revision);ElMessage.success('简历已删除');emit('deleted')}catch(e){if(e!=='cancel'&&e!=='close')showError(e)}finally{busy.value=false}}
</script>
<template><el-tooltip :content="resume.parseStatus==='PARSING'||resume.parseStatus==='UPLOADED'?'处理中，暂不可删除':'删除简历'" :disabled="!compact" placement="top"><span class="delete-control"><el-button class="delete-resume" type="danger" text :icon="Delete" :loading="busy" :disabled="disabled||resume.parseStatus==='PARSING'||resume.parseStatus==='UPLOADED'" aria-label="删除简历" @click="remove"><template v-if="!compact">删除简历</template></el-button></span></el-tooltip></template>

<script setup>
import { ref, watch, nextTick } from "vue";
import { marked } from "marked";

// ================= 1. 基础配置与状态 =================
const backendUrl = "http://localhost:8080"; // 请确保与你的 Spring Boot 端口一致

const modules = [
  {
    id: "chat",
    type: "chat",
    title: "AI 聊天",
    icon: "💬",
    desc: "多模态对话机器人，支持图片、音频等",
    endpoint: "/ai/chat",
  },
  {
    id: "game",
    type: "game",
    title: "推理破案",
    icon: "🕵️",
    desc: "化身侦探，通过调查与推理找出案件真相",
    endpoint: "/ai/game",
  },
  {
    id: "service",
    type: "service",
    title: "智能客服",
    icon: "👩‍💼",
    desc: "24小时在线的智能课程咨询师",
    endpoint: "/ai/service",
  },
  {
    id: "pdf",
    type: "pdf",
    title: "ChatPDF",
    icon: "📄",
    desc: "打造你的个人知识库，与知识库自由对话",
    endpoint: "/ai/pdf/chat",
  },
];

const currentModule = ref(null);
const sessionList = ref([]); // 侧边栏历史记录列表
const currentSessionId = ref(""); // 当前选中的会话 ID
const chatHistory = ref([]); // 当前会话的具体消息
const inputMessage = ref("");
const isTyping = ref(false);
const chatContainer = ref(null);

// 附件与 PDF 状态
const selectedFiles = ref([]); // 聊天模块选中的文件
const fileInputRef = ref(null); // 隐藏的文件选择框
const hasPdfUploaded = ref(false); // PDF 模块：是否已上传 PDF
const isUploading = ref(false); // 上传中状态

// ================= 2. 会话管理逻辑 =================

// 生成唯一会话ID
const generateId = () => Math.random().toString(36).substring(2, 15);

// 获取侧边栏会话列表
const fetchSessionList = async (type) => {
  try {
    const res = await fetch(`${backendUrl}/ai/history/${type}`);
    if (res.ok) {
      sessionList.value = await res.json();
    }
  } catch (error) {
    console.error("获取历史记录失败:", error);
  }
};

// 加载指定会话的聊天记录
const loadSession = async (chatId) => {
  currentSessionId.value = chatId;
  chatHistory.value = [];
  hasPdfUploaded.value = currentModule.value.id === "pdf"; // 如果在PDF模块点击历史记录，假设已有PDF

  try {
    const res = await fetch(
      `${backendUrl}/ai/history/${currentModule.value.type}/${chatId}`,
    );
    if (res.ok) {
      const history = await res.json();
      chatHistory.value = history || [];
    }
  } catch (error) {
    console.error("获取会话详情失败:", error);
  }
  scrollToBottom();
};

// 新建对话
const createNewSession = () => {
  currentSessionId.value = generateId();
  chatHistory.value = [];
  hasPdfUploaded.value = false;
  selectedFiles.value = [];

  // 如果是普通聊天或客服，给个欢迎语
  if (
    currentModule.value.id === "chat" ||
    currentModule.value.id === "service"
  ) {
    chatHistory.value.push({
      role: "assistant",
      content: `你好呀！很高兴为你服务。有什么我可以帮到你的吗？`,
    });
  }
};

// 进入模块
const selectModule = (mod) => {
  currentModule.value = mod;
  fetchSessionList(mod.type);
  createNewSession();
};

// 返回大厅
const backToHub = () => {
  currentModule.value = null;
};

const scrollToBottom = async () => {
  await nextTick();
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
  }
};

// ================= 3. 核心交互逻辑 =================

// 触发文件选择 (普通聊天)
const triggerFileSelect = () => {
  fileInputRef.value.click();
};
const handleFileChange = (e) => {
  selectedFiles.value = Array.from(e.target.files);
};

// 发送消息 (核心流式交互)
const sendMessage = async () => {
  if (!inputMessage.value.trim() && selectedFiles.value.length === 0) return;
  if (isTyping.value) return;

  const userMsg = inputMessage.value;
  chatHistory.value.push({ role: "user", content: userMsg });
  inputMessage.value = "";
  isTyping.value = true;
  scrollToBottom();

  const assistantMsgIndex =
    chatHistory.value.push({ role: "assistant", content: "" }) - 1;
  const formData = new FormData();
  formData.append("prompt", userMsg);
  formData.append("chatId", currentSessionId.value);

  // 处理附件 (针对普通聊天模块)
  if (currentModule.value.id === "chat" && selectedFiles.value.length > 0) {
    selectedFiles.value.forEach((file) => formData.append("files", file));
    selectedFiles.value = []; // 发送后清空
  }

  try {
    const response = await fetch(
      `${backendUrl}${currentModule.value.endpoint}`,
      {
        method: "POST",
        body: formData,
      },
    );

    if (!response.ok) throw new Error("网络请求失败");

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = decoder.decode(value, { stream: true });
      chatHistory.value[assistantMsgIndex].content += chunk;
      scrollToBottom();
    }

    // 消息发送完毕后，如果这是一个新会话，刷新一下侧边栏列表
    if (!sessionList.value.includes(currentSessionId.value)) {
      fetchSessionList(currentModule.value.type);
    }
  } catch (error) {
    chatHistory.value[assistantMsgIndex].content =
      "⚠️ 连接服务器失败，请检查后端运行状态。";
  } finally {
    isTyping.value = false;
  }
};

// ================= 4. PDF 专属逻辑 =================
const handlePdfUpload = async (e) => {
  const file = e.target.files[0];
  if (!file || file.type !== "application/pdf") {
    alert("请上传 PDF 文件！");
    return;
  }

  isUploading.value = true;
  const formData = new FormData();
  formData.append("file", file);

  try {
    // 调用后端的 /ai/pdf/upload/{chatId}
    const res = await fetch(
      `${backendUrl}/ai/pdf/upload/${currentSessionId.value}`,
      {
        method: "POST",
        body: formData,
      },
    );
    const result = await res.json();
    if (result.ok === 1) {
      hasPdfUploaded.value = true;
      fetchSessionList(currentModule.value.type); // 刷新侧边栏
    } else {
      alert(result.msg || "上传失败");
    }
  } catch (error) {
    alert("上传请求失败，请检查网络或跨域设置");
  } finally {
    isUploading.value = false;
  }
};

// 模拟器开场
const startGame = () => {
  if (!inputMessage.value.trim()) inputMessage.value = "开始推理";
  sendMessage();
};
</script>

<template>
  <div
    class="h-screen flex flex-col font-sans bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 text-slate-200 overflow-hidden"
  >
    <header
      class="h-16 shrink-0 backdrop-blur-md bg-white/5 border-b border-white/10 flex justify-between items-center px-6 shadow-md z-50"
    >
      <h1
        class="text-2xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-cyan-400 to-blue-400 cursor-pointer"
        @click="backToHub"
      >
        AI Hub <span class="text-sm text-slate-400 ml-2">Enterprise</span>
      </h1>
      <button
        v-if="currentModule"
        @click="backToHub"
        class="text-sm px-4 py-1.5 rounded-full border border-white/20 hover:bg-white/10 transition-colors"
      >
        返回应用中心
      </button>
    </header>

    <main
      v-if="!currentModule"
      class="flex-1 max-w-6xl mx-auto w-full p-8 flex flex-col justify-center animate-fade-in"
    >
      <h2 class="text-4xl font-extrabold mb-12 text-center">
        探索 <span class="text-cyan-400">AI 赋能</span> 新边界
      </h2>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div
          v-for="mod in modules"
          :key="mod.id"
          @click="selectModule(mod)"
          class="group relative bg-white/5 border border-white/10 p-8 rounded-3xl cursor-pointer hover:-translate-y-2 hover:border-cyan-500/50 transition-all duration-300"
        >
          <div class="text-5xl mb-6 group-hover:scale-110 transition-transform">
            {{ mod.icon }}
          </div>
          <h3 class="text-xl font-bold mb-2 text-white">{{ mod.title }}</h3>
          <p class="text-sm text-slate-400 leading-relaxed">{{ mod.desc }}</p>
        </div>
      </div>
    </main>

    <div v-else class="flex-1 flex overflow-hidden animate-fade-in">
      <aside
        class="w-64 flex-shrink-0 bg-white/5 border-r border-white/10 flex flex-col h-full"
      >
        <div
          class="p-4 border-b border-white/10 flex items-center justify-between"
        >
          <span class="font-bold flex items-center text-lg"
            ><span class="mr-2">{{ currentModule.icon }}</span
            >{{ currentModule.title }}</span
          >
        </div>
        <div class="p-4">
          <button
            @click="createNewSession"
            class="w-full py-2 bg-cyan-600 hover:bg-cyan-500 text-white rounded-lg flex justify-center items-center transition-colors shadow-lg shadow-cyan-900/20"
          >
            <svg
              class="w-4 h-4 mr-2"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M12 4v16m8-8H4"
              ></path>
            </svg>
            新对话
          </button>
        </div>
        <div class="flex-1 overflow-y-auto px-3 pb-4 custom-scrollbar">
          <div class="text-xs text-slate-500 mb-2 px-2 uppercase font-semibold">
            历史记录
          </div>
          <div
            v-if="sessionList.length === 0"
            class="text-slate-500 text-sm px-2 mt-4 text-center"
          >
            暂无记录
          </div>
          <div
            v-for="chatId in sessionList"
            :key="chatId"
            @click="loadSession(chatId)"
            class="px-3 py-2.5 mb-1 rounded-lg cursor-pointer text-sm truncate transition-colors"
            :class="
              currentSessionId === chatId
                ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30'
                : 'hover:bg-white/10 text-slate-300'
            "
          >
            <span class="mr-2 opacity-60">💭</span> 会话
            {{ chatId.substring(0, 6) }}
          </div>
        </div>
      </aside>

      <main class="flex-1 flex flex-col relative bg-slate-900/50">
        <div
          v-if="currentModule.id === 'pdf' && !hasPdfUploaded"
          class="flex-1 flex flex-col items-center justify-center p-8"
        >
          <h2 class="text-3xl font-bold mb-8 text-white">
            与任何 <span class="text-purple-400">PDF</span> 对话
          </h2>
          <div
            class="w-full max-w-md p-12 border-2 border-dashed border-white/20 rounded-2xl bg-white/5 hover:bg-white/10 transition-colors text-center relative"
          >
            <input
              type="file"
              accept="application/pdf"
              @change="handlePdfUpload"
              class="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
              :disabled="isUploading"
            />
            <div class="text-5xl mb-4 text-purple-400">📄</div>
            <p class="text-slate-300 mb-2">
              {{
                isUploading
                  ? "正在上传并解析向量库..."
                  : "点击或拖拽 PDF 文件到此处"
              }}
            </p>
            <div
              v-if="isUploading"
              class="mt-4 w-full bg-slate-700 h-2 rounded-full overflow-hidden"
            >
              <div
                class="bg-purple-500 h-full animate-pulse w-1/2 rounded-full"
              ></div>
            </div>
          </div>
        </div>

        <div
          v-else-if="currentModule.id === 'game' && chatHistory.length === 0"
          class="flex-1 flex flex-col items-center justify-center p-8"
        >
          <h2 class="text-3xl font-bold mb-8 text-white">🕵️ 推理破案</h2>
          <div
            class="w-full max-w-md bg-white/5 p-6 border border-white/10 rounded-2xl shadow-2xl"
          >
            <textarea
              v-model="inputMessage"
              rows="3"
              class="w-full bg-slate-900/50 border border-white/10 rounded-lg p-3 text-white focus:border-pink-500 focus:ring-1 focus:ring-pink-500 outline-none resize-none mb-4"
              placeholder="输入案件背景开始推理，或直接点击开始随机生成案件..."
            ></textarea>
            <button
              @click="startGame"
              class="w-full py-3 bg-cyan-600 hover:bg-cyan-500 text-white font-bold rounded-lg shadow-lg transition-colors"
            >
              开始推理
            </button>
          </div>
        </div>

        <div v-else class="flex-1 flex h-full overflow-hidden">
          <div
            v-if="currentModule.id === 'pdf'"
            class="w-full lg:w-1/2 border-r border-white/10 bg-slate-800 hidden lg:block"
          >
            <iframe
              :src="`${backendUrl}/ai/pdf/file/${currentSessionId}`"
              class="w-full h-full border-none"
            ></iframe>
          </div>

          <div
            class="flex flex-col h-full relative"
            :class="
              currentModule.id === 'pdf'
                ? 'w-full lg:w-1/2 shrink-0'
                : 'flex-1 max-w-4xl mx-auto w-full'
            "
          >
            <div
              ref="chatContainer"
              class="flex-1 overflow-y-auto p-6 space-y-6 custom-scrollbar"
            >
              <div
                v-for="(msg, index) in chatHistory"
                :key="index"
                class="flex flex-col"
                :class="msg.role === 'user' ? 'items-end' : 'items-start'"
              >
                <div
                  class="flex items-center space-x-2 mb-1"
                  :class="
                    msg.role === 'user'
                      ? 'flex-row-reverse space-x-reverse'
                      : ''
                  "
                >
                  <div
                    class="w-8 h-8 rounded-full flex items-center justify-center text-sm"
                    :class="
                      msg.role === 'user' ? 'bg-blue-600' : 'bg-slate-700'
                    "
                  >
                    {{ msg.role === "user" ? "🙎‍♂️" : "🤖" }}
                  </div>
                  <span class="text-xs text-slate-400">{{
                    msg.role === "user" ? "You" : currentModule.title
                  }}</span>
                </div>
                <div
                  class="max-w-[85%] rounded-2xl px-5 py-3 shadow-md"
                  :class="
                    msg.role === 'user'
                      ? 'bg-blue-600 text-white rounded-tr-sm'
                      : 'bg-white/10 border border-white/10 text-slate-200 rounded-tl-sm'
                  "
                >
                  <div
                    v-if="msg.role === 'assistant'"
                    class="prose prose-invert prose-sm max-w-none prose-p:leading-relaxed"
                    v-html="marked(msg.content)"
                  ></div>
                  <div
                    v-else
                    class="whitespace-pre-wrap text-sm leading-relaxed"
                  >
                    {{ msg.content }}
                  </div>
                </div>
              </div>

              <div v-if="isTyping" class="flex flex-col items-start">
                <div
                  class="w-8 h-8 rounded-full bg-slate-700 flex items-center justify-center text-sm mb-1"
                >
                  🤖
                </div>
                <div
                  class="bg-white/5 border border-white/10 rounded-2xl px-5 py-4 rounded-tl-sm flex items-center space-x-1.5"
                >
                  <div
                    class="w-2 h-2 bg-cyan-400 rounded-full animate-bounce"
                  ></div>
                  <div
                    class="w-2 h-2 bg-cyan-400 rounded-full animate-bounce"
                    style="animation-delay: 0.2s"
                  ></div>
                  <div
                    class="w-2 h-2 bg-cyan-400 rounded-full animate-bounce"
                    style="animation-delay: 0.4s"
                  ></div>
                </div>
              </div>
            </div>

            <div
              class="p-4 border-t border-white/10 bg-slate-900/80 backdrop-blur-sm"
            >
              <div
                v-if="selectedFiles.length > 0"
                class="flex flex-wrap gap-2 mb-2"
              >
                <div
                  v-for="(f, i) in selectedFiles"
                  :key="i"
                  class="bg-white/10 text-xs px-2 py-1 rounded border border-white/20 text-cyan-300 flex items-center"
                >
                  📎 {{ f.name }}
                </div>
              </div>

              <div
                class="relative flex items-end bg-white/5 border border-white/20 rounded-xl focus-within:border-cyan-500 focus-within:ring-1 focus-within:ring-cyan-500 transition-all"
              >
                <div
                  v-if="currentModule.id === 'chat'"
                  class="px-3 pb-3 flex items-center justify-center text-slate-400 hover:text-cyan-400 cursor-pointer"
                  @click="triggerFileSelect"
                >
                  <svg
                    class="w-5 h-5"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13"
                    ></path>
                  </svg>
                  <input
                    type="file"
                    ref="fileInputRef"
                    @change="handleFileChange"
                    class="hidden"
                    multiple
                    accept="image/*,audio/*"
                  />
                </div>

                <textarea
                  v-model="inputMessage"
                  @keydown.enter.exact.prevent="sendMessage"
                  :disabled="isTyping"
                  class="flex-1 bg-transparent py-3 px-2 text-white placeholder-slate-500 outline-none resize-none max-h-32 custom-scrollbar text-sm"
                  rows="1"
                  placeholder="输入消息... (Shift+Enter 换行)"
                ></textarea>

                <button
                  @click="sendMessage"
                  :disabled="
                    isTyping ||
                    (!inputMessage.trim() && selectedFiles.length === 0)
                  "
                  class="m-2 p-2 bg-cyan-600 hover:bg-cyan-500 disabled:bg-slate-700 disabled:text-slate-500 text-white rounded-lg transition-colors"
                >
                  <svg
                    class="w-4 h-4 rotate-90"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
                    ></path>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style>
/* 保持淡入动画 */
.animate-fade-in {
  animation: fadeIn 0.4s ease-out forwards;
}
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 自定义滚动条 */
.custom-scrollbar::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: rgba(34, 211, 238, 0.4);
}

/* Markdown 样式微调 */
.prose pre {
  margin: 0.5rem 0;
  padding: 0.75rem;
  border-radius: 0.5rem;
  background-color: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.1);
}
</style>

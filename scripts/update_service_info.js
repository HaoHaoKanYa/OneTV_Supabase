#!/usr/bin/env node

/**
 * OneTV 服务信息更新工具
 * 
 * 此脚本用于通过命令行更新 Supabase 的服务信息
 * 使用方法: node update_service_info.js -m "您的服务信息内容"
 */

const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');
const path = require('path');
const readline = require('readline');

// Supabase 配置
let SUPABASE_URL;
let SUPABASE_KEY;

try {
  // 尝试从环境变量加载配置
  SUPABASE_URL = process.env.SUPABASE_URL || '';
  SUPABASE_KEY = process.env.SUPABASE_ADMIN_KEY || '';
  
  // 如果环境变量不存在，尝试从配置文件加载
  if (!SUPABASE_URL || !SUPABASE_KEY) {
    const configPath = path.join(__dirname, 'config.json');
    if (fs.existsSync(configPath)) {
      const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
      SUPABASE_URL = config.SUPABASE_URL;
      SUPABASE_KEY = config.SUPABASE_ADMIN_KEY;
    }
  }
  
  if (!SUPABASE_URL || !SUPABASE_KEY) {
    throw new Error('Supabase 配置缺失。请设置环境变量或提供配置文件。');
  }
} catch (error) {
  console.error('配置加载失败:', error.message);
  process.exit(1);
}

// 创建 Supabase 客户端
const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

// 命令行参数解析
const args = process.argv.slice(2);
let message = '';
let interactive = true;

for (let i = 0; i < args.length; i++) {
  if (args[i] === '-m' || args[i] === '--message') {
    if (i + 1 < args.length) {
      message = args[i + 1];
      interactive = false;
    }
  }
}

// 交互式输入
async function promptForMessage() {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
  });
  
  return new Promise(resolve => {
    rl.question('请输入服务信息内容: ', (answer) => {
      rl.close();
      resolve(answer);
    });
  });
}

// 更新服务信息
async function updateServiceInfo(content) {
  try {
    console.log('正在更新服务信息...');
    
    const { data, error } = await supabase
      .from('service_messages')
      .insert({ message: content })
      .select();
    
    if (error) {
      throw error;
    }
    
    console.log('服务信息更新成功!');
    console.log('更新时间:', new Date().toISOString());
    console.log('内容:', content);
  } catch (error) {
    console.error('更新失败:', error.message);
    process.exit(1);
  }
}

// 主函数
async function main() {
  console.log('OneTV 服务信息更新工具');
  console.log('------------------------');
  
  // 如果未通过命令行提供消息，则交互式请求输入
  if (interactive) {
    message = await promptForMessage();
  }
  
  if (!message || message.trim() === '') {
    console.error('错误: 服务信息内容不能为空!');
    process.exit(1);
  }
  
  await updateServiceInfo(message);
}

// 运行主函数
main().catch(error => {
  console.error('程序执行错误:', error);
  process.exit(1);
}); 
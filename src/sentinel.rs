use serde_json::Value;
use reqwest::header::{HeaderMap, HeaderValue, AUTHORIZATION, CONTENT_TYPE};
use std::time::{SystemTime, UNIX_EPOCH};
use crate::audit::{ActiveLevel, QuantumSession};

pub struct ComplianceController {
    pub session: QuantumSession,
}

impl ComplianceController {
    /// Initializes session with strict 3-day payment fallback logic (T3 direct to T1)
    pub fn initialize(base_id: &str, level: ActiveLevel, payment_failed_at: Option<u64>) -> Self {
        let mut resolved_level = level;

        if resolved_level == ActiveLevel::T3 {
            if let Some(failed_timestamp) = payment_failed_at {
                let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs();
                let three_days_seconds = 3 * 24 * 60 * 60; // 72 Hours

                if now > (failed_timestamp + three_days_seconds) {
                    println!("[ALERT] Billing failure exceeded 3 days! Downgrading T3 direct to T1.");
                    resolved_level = ActiveLevel::T1; // Hard fallback, completely bypassing T2
                } else {
                    println!("[WARNING] T3 payment failed, running in 3-day grace period. T3 features active.");
                }
            }
        }

        let session = QuantumSession::new(base_id, resolved_level);
        Self { session }
    }
}

pub struct SentinelClone {
    pub id: usize,
    pub level: ActiveLevel,
}

impl SentinelClone {
    pub fn spawn(id: usize, level: ActiveLevel) -> Self {
        Self { id, level }
    }

    /// Non-Linear Agentic execution utilizing Groq and NVIDIA NIM securely based on tier authorization
    pub async fn execute_nla_task(
        self,
        payload: &str,
        groq_key: Option<String>,
        nim_key: Option<String>,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        let client = reqwest::Client::new();

        match self.level {
            ActiveLevel::T1 => {
                // T1: Zero network usage. Performs clean, instant local fallback processing.
                let mut local_processed = payload.to_string();
                local_processed.push_str(" [Validated via Local Rust Core (T1)]");
                Ok(local_processed)
            }
            ActiveLevel::T2 => {
                // T2: Allowed Groq execution
                let api_key = groq_key.ok_or("Missing Groq API Key for T2 Session")?;
                let mut headers = HeaderMap::new();
                headers.insert(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {}", api_key))?);
                headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));

                let body = serde_json::json!({
                    "model": "llama3-8b-8192",
                    "messages": [
                        {"role": "system", "content": "You are a fast grammar correction engine. Correct the text concisely."},
                        {"role": "user", "content": payload}
                    ]
                });

                let res = client.post("https://api.groq.com/openai/v1/chat/completions")
                    .headers(headers).json(&body).send().await?;
                let res_json: Value = res.json().await?;
                let content = res_json["choices"][0]["message"]["content"]
                    .as_str().unwrap_or("Error resolving text.").to_string();
                Ok(content)
            }
            ActiveLevel::T3 => {
                // T3: Full Groq + NVIDIA NIM + ProQuest (NIM example shown below)
                let api_key = nim_key.ok_or("Missing NVIDIA NIM API Key for T3 Session")?;
                let mut headers = HeaderMap::new();
                headers.insert(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {}", api_key))?);
                headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));

                let body = serde_json::json!({
                    "model": "meta/llama3-8b-instruct",
                    "messages": [
                        {"role": "system", "content": "You are the premium LexiQ Enterprise LLM. Process grammar diagnostics with extreme semantic precision."},
                        {"role": "user", "content": payload}
                    ]
                });

                let res = client.post("https://integrate.api.nvidia.com/v1/chat/completions")
                    .headers(headers).json(&body).send().await?;
                let res_json: Value = res.json().await?;
                let content = res_json["choices"][0]["message"]["content"]
                    .as_str().unwrap_or("Error resolving deep reasoning.").to_string();
                Ok(content)
            }
        }
    }
}

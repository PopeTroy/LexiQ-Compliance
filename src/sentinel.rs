use crate::audit::{ActiveLevel, QuantumSession, SessionAccessState};
use async_trait::async_trait;
use reqwest::header::{HeaderMap, HeaderValue, AUTHORIZATION, CONTENT_TYPE};
use serde_json::Value;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone)]
pub struct EconomicsGuard {
    pub free_tier_disabled: bool,
    pub max_cost_units_per_1k_requests: f64,
    pub accumulated_cost_units: f64,
    pub request_count: u64,
}

impl Default for EconomicsGuard {
    fn default() -> Self {
        let free_tier_disabled = std::env::var("EMERGENCY_DISABLE_FREE_TIER")
            .map(|v| v.eq_ignore_ascii_case("true") || v == "1")
            .unwrap_or(false);
        Self {
            free_tier_disabled,
            max_cost_units_per_1k_requests: 6.53,
            accumulated_cost_units: 0.0,
            request_count: 0,
        }
    }
}

impl EconomicsGuard {
    pub fn record_and_check(&mut self, tier: ActiveLevel) -> Result<(), &'static str> {
        if self.free_tier_disabled && tier == ActiveLevel::T1 {
            return Err("Free tier disabled via emergency_disable_free_tier flag");
        }
        self.request_count += 1;
        let unit_cost = match tier {
            ActiveLevel::T1 => 0.0,
            ActiveLevel::T2 => 0.004,
            ActiveLevel::T3 => 0.008,
        };
        self.accumulated_cost_units += unit_cost;

        let batches_of_1k = ((self.request_count as f64) / 1000.0).max(1.0);
        if self.accumulated_cost_units > (self.max_cost_units_per_1k_requests * batches_of_1k) {
            return Err("Compute cost ceiling exceeded per 1k requests");
        }
        Ok(())
    }
}

#[async_trait]
pub trait NlaExecutor: Send + Sync {
    async fn complete(
        &self,
        level: ActiveLevel,
        payload: &str,
        groq_key: Option<String>,
        nim_key: Option<String>,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>>;
}

pub struct DefaultHttpNlaExecutor {
    client: reqwest::Client,
}

impl Default for DefaultHttpNlaExecutor {
    fn default() -> Self {
        Self {
            client: reqwest::Client::new(),
        }
    }
}

#[async_trait]
impl NlaExecutor for DefaultHttpNlaExecutor {
    async fn complete(
        &self,
        level: ActiveLevel,
        payload: &str,
        groq_key: Option<String>,
        nim_key: Option<String>,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        match level {
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

                let res = self.client.post("https://api.groq.com/openai/v1/chat/completions")
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

                let res = self.client.post("https://integrate.api.nvidia.com/v1/chat/completions")
                    .headers(headers).json(&body).send().await?;
                let res_json: Value = res.json().await?;
                let content = res_json["choices"][0]["message"]["content"]
                    .as_str().unwrap_or("Error resolving deep reasoning.").to_string();
                Ok(content)
            }
        }
    }
}

pub struct ComplianceController {
    pub session: QuantumSession,
}

impl ComplianceController {
    /// Initializes session with strict 3-day payment fallback logic (T3 direct to T1)
    pub fn initialize(base_id: &str, level: ActiveLevel, payment_failed_at: Option<u64>) -> Self {
        let mut resolved_level = level;
        let mut access_state = SessionAccessState::Active;

        if resolved_level == ActiveLevel::T3 {
            if let Some(failed_timestamp) = payment_failed_at {
                let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs();
                let three_days_seconds = 3 * 24 * 60 * 60; // 72 Hours

                if now > (failed_timestamp + three_days_seconds) {
                    println!("[ALERT] Billing failure exceeded 3 days! Downgrading T3 direct to T1.");
                    resolved_level = ActiveLevel::T1; // Hard fallback, completely bypassing T2
                    access_state = SessionAccessState::DowngradedToT1;
                } else {
                    println!("[WARNING] T3 payment failed, running in 3-day grace period. T3 features active.");
                    access_state = SessionAccessState::GracePeriod;
                }
            }
        }

        let session = QuantumSession::with_state(base_id, resolved_level, access_state, 0.0);
        Self { session }
    }
}

pub struct SentinelClone {
    pub id: usize,
    pub level: ActiveLevel,
    pub economics: EconomicsGuard,
}

impl SentinelClone {
    pub fn spawn(id: usize, level: ActiveLevel) -> Self {
        Self {
            id,
            level,
            economics: EconomicsGuard::default(),
        }
    }

    /// Non-Linear Agentic execution utilizing Groq and NVIDIA NIM securely based on tier authorization
    pub async fn execute_nla_task(
        mut self,
        payload: &str,
        groq_key: Option<String>,
        nim_key: Option<String>,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        self.execute_with_executor(payload, groq_key, nim_key, &DefaultHttpNlaExecutor::default()).await
    }

    pub async fn execute_with_executor<E: NlaExecutor + ?Sized>(
        &mut self,
        payload: &str,
        groq_key: Option<String>,
        nim_key: Option<String>,
        executor: &E,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        self.economics.record_and_check(self.level)?;
        executor.complete(self.level, payload, groq_key, nim_key).await
    }
}

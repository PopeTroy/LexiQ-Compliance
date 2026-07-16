use chrono::{DateTime, Utc, FixedOffset, Timelike}; // ◄— Imported Timelike trait to unlock time calculation methods
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum ActiveLevel {
    T1, // Core: Local Only (Zero Cloud Cost)
    T2, // Pro: Standard (Groq AI)
    T3, // Enterprise: Premium (Groq + NVIDIA NIM + ProQuest)
}

#[derive(Debug, Clone, Serialize)]
pub struct QuantumSession {
    pub session_id: String,
    pub level: ActiveLevel,
    pub timestamp_sast: String,
    pub quantum_cycle: u64,
}

impl QuantumSession {
    /// Constructs an active user session, mapping the temporal clock and tracking suffix
    pub fn new(base_id: &str, level: ActiveLevel) -> Self {
        let suffix = match level {
            ActiveLevel::T1 => "T1",
            ActiveLevel::T2 => "T2",
            ActiveLevel::T3 => "T3",
        };
        let session_id = format!("{}-{}", base_id, suffix);

        // Calculate South African Standard Time (SAST = UTC+2)
        let utc_now: DateTime<Utc> = Utc::now();
        let sast_offset = FixedOffset::east_opt(2 * 3600).unwrap();
        let sast_now = utc_now.with_timezone(&sast_offset);
        
        // 1:6000 Quantum Dilation: Maps 1 second to 24 cycles
        let seconds_today = sast_now.time().num_seconds_from_midnight() as u64;
        let quantum_cycle = seconds_today * 24;

        QuantumSession {
            session_id,
            level,
            timestamp_sast: sast_now.to_rfc3339(),
            quantum_cycle,
        }
    }
}

// src/services/appointmentAnalytics.js
import { getDatabase, ref, set, increment } from "firebase/database";
import { logEvent } from "firebase/analytics";
import { analytics } from "../firebase"; // Adjust path if needed

// Track when an appointment is created
export function trackAppointmentCreated(appointmentData) {
  const db = getDatabase();
  const currentDate = new Date();
  const weekNumber = getWeekNumber(currentDate);
  const monthNumber = currentDate.getMonth() + 1;
  const year = currentDate.getFullYear();
  
  // Update lawyer usage count
  const lawyerRef = ref(db, `lawyer_usage/${appointmentData.lawyerId}`);
  set(lawyerRef, {
    usage_count: increment(1)
  });
  
  // Store appointment with time metadata
  const appointmentRef = ref(db, `appointments/${appointmentData.id}`);
  set(appointmentRef, {
    ...appointmentData,
    week_number: weekNumber,
    month_number: monthNumber,
    year: year,
    timestamp: currentDate.getTime(),
    status: "created"
  });
  
  // Log event to Firebase Analytics
  logEvent(analytics, 'appointment_created', {
    appointment_id: appointmentData.id,
    appointment_type: appointmentData.type,
    lawyer_id: appointmentData.lawyerId,
    client_id: appointmentData.clientId,
    date: appointmentData.date,
    week_number: weekNumber,
    month_number: monthNumber,
    year: year
  });
}

// Track when an appointment is completed
export function trackAppointmentCompleted(appointmentData) {
  const db = getDatabase();
  const currentDate = new Date();
  
  // Update appointment status
  const appointmentRef = ref(db, `appointments/${appointmentData.id}`);
  set(appointmentRef, {
    ...appointmentData,
    status: "completed",
    completion_date: currentDate.getTime()
  });
  
  // Log event to Firebase Analytics
  logEvent(analytics, 'appointment_completed', {
    appointment_id: appointmentData.id,
    lawyer_id: appointmentData.lawyerId,
    duration_minutes: appointmentData.durationMinutes || 0
  });
}

// Helper function to get week number
function getWeekNumber(date) {
  const firstDayOfYear = new Date(date.getFullYear(), 0, 1);
  const pastDaysOfYear = (date - firstDayOfYear) / 86400000;
  return Math.ceil((pastDaysOfYear + firstDayOfYear.getDay() + 1) / 7);
}
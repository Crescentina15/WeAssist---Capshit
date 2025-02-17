import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getDatabase } from "firebase/database";

const firebaseConfig = {
  apiKey: "AIzaSyBGx-8FIuBqFIVEYLZV3KLLkukRDNDRRYU",
  authDomain: "weassist-f2a77.firebaseapp.com",
  databaseURL: "https://weassist-f2a77-default-rtdb.firebaseio.com",
  projectId: "weassist-f2a77",
  storageBucket: "weassist-f2a77.firebasestorage.app",
  messagingSenderId: "792842654133",
  appId: "1:792842654133:web:a8889cca2d52ade15089fc",
  measurementId: "G-E43ZF8W44T"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getDatabase(app);

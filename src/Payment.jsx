// SuccessPage.js
import React, { useEffect, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import "./index.css";

// Simple check mark SVG component
const CheckMarkIcon = () => (
  <div className="check-icon">
    <svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#4CAF50" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
      <polyline points="22 4 12 14.01 9 11.01"></polyline>
    </svg>
  </div>
);

const SuccessPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const [verificationComplete, setVerificationComplete] = useState(false);
  
  // Get payment details from URL query parameters
  const paymentId = queryParams.get("payment_id");
  const planName = queryParams.get("plan");
  const amount = queryParams.get("amount");
  const status = queryParams.get("status");
  
  useEffect(() => {
    // You can verify the payment with your backend here
    const verifyPayment = async () => {
      if (paymentId) {
        try {
          const response = await fetch(`http://localhost:5000/verify-payment?payment_id=${paymentId}`);
          const data = await response.json();
          
          // Update UI based on verification result
          console.log("Payment verification:", data);
          setVerificationComplete(true);
        } catch (error) {
          console.error("Error verifying payment:", error);
          setVerificationComplete(true); // Still mark as complete even if verification fails
        }
      } else {
        setVerificationComplete(true);
      }
    };
    
    verifyPayment();
  }, [paymentId]);
  
  const handleReturnHome = () => {
    navigate("/dashboard");
  };
  
  const formatDate = (date) => {
    return new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    }).format(date);
  };

  if (!verificationComplete) {
    return (
      <div className="success-page-container">
        <div className="success-card">
          <h2>Confirming your payment...</h2>
          <div className="loading-spinner"></div>
        </div>
      </div>
    );
  }

  return (
    <div className="success-page-container">
      <div className="success-card">
        <div className="success-icon">
          <CheckMarkIcon />
        </div>
        
        <h1 className="success-title">Payment Successful!</h1>
        
        <div className="success-message">
          <p>Thank you for your subscription. Your payment has been processed successfully.</p>
        </div>
        
        <div className="order-details">
          <h2>Order Details</h2>
          <div className="detail-row">
            <span className="detail-label">Plan:</span>
            <span className="detail-value">{planName || "Subscription Plan"}</span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Amount:</span>
            <span className="detail-value">{amount ? `₱${amount}` : "Payment processed"}</span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Date:</span>
            <span className="detail-value">{formatDate(new Date())}</span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Transaction ID:</span>
            <span className="detail-value">{paymentId || "Generated by system"}</span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Status:</span>
            <span className="detail-value status-success">{status || "Succeeded"}</span>
          </div>
        </div>
        
        <div className="next-steps">
          <h3>What's Next?</h3>
          <ul>
            <li>You will receive a confirmation email shortly.</li>
            <li>Your subscription is now active.</li>
            <li>You can access all premium features from your dashboard.</li>
          </ul>
        </div>
        
        <div className="action-buttons">
          <button className="primary-button" onClick={handleReturnHome}>
            Go to Dashboard
          </button>
          <button className="secondary-button" onClick={() => window.print()}>
            Print Receipt
          </button>
        </div>
        
        <div className="support-info">
          <p>If you have any questions, please contact our support team at <a href="mailto:support@example.com">support@example.com</a></p>
        </div>
      </div>
    </div>
  );
};

export default SuccessPage;
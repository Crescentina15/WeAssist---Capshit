import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { loadStripe } from "@stripe/stripe-js";
import "./index.css";

// Using your existing Stripe key from the document
const stripePromise = loadStripe("pk_test_51R1JB1FK88cwX0GIKPBVnKvk71rR4fEuOLZQkfgW814lspsx14jcUk61Is7sq6uS7IAHSrdHzOWDCsZPRgDj5YFi00kewOXwwe");

// Payment method selection component
const PaymentMethodSelector = ({ selectedPlan, onCancel }) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  
  // Update your handleStripeCheckout function in the PaymentMethodSelector component:

// Update the success_url and cancel_url in your frontend code
const handleStripeCheckout = async () => {
  setLoading(true);
  setError(null);
  
  try {
    const stripe = await stripePromise;
    
    console.log('Creating checkout session for plan:', selectedPlan);
    
    // Call your backend to create a Checkout Session
    const response = await fetch('http://localhost:5000/create-checkout-session', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        planId: selectedPlan.id,
        planName: selectedPlan.name,
        amount: selectedPlan.amount,
        success_url: 'http://localhost:5174/payment-success', // Updated port
        cancel_url: 'http://localhost:5174/plans' // Updated port
      }),
    });
    
    console.log('Response status:', response.status);
    
    if (!response.ok) {
      const errorText = await response.text();
      console.error('Error response:', errorText);
      throw new Error(`Server responded with status: ${response.status}, message: ${errorText}`);
    }
    
    const session = await response.json();
    console.log('Received session:', session);
    
    // Redirect to Stripe Checkout
    console.log('Redirecting to Stripe checkout...');
    const result = await stripe.redirectToCheckout({
      sessionId: session.id,
    });
    
    if (result.error) {
      console.error('Stripe redirect error:', result.error);
      throw new Error(result.error.message);
    }
  } catch (error) {
    console.error('Detailed error:', error);
    setError(error.message || 'Something went wrong. Please try again.');
  } finally {
    setLoading(false);
  }
};
  
  return (
    <div className="payment-method-container">
      <h2>Choose Payment Method</h2>
      <div className="selected-plan-summary">
        <h3>Selected Plan: {selectedPlan.name}</h3>
        <p>Price: {selectedPlan.price}</p>
        <p>{selectedPlan.description}</p>
        <button className="change-plan-btn" onClick={onCancel}>Change Plan</button>
      </div>
      
      <div className="payment-methods">
        <button 
          className="payment-method-btn stripe-btn" 
          onClick={handleStripeCheckout}
          disabled={loading}
        >
          <div className="payment-method-icon">
            <img src="https://cdn.jsdelivr.net/gh/stripe-samples/checkout-one-time-payments/client/html/images/stripe.svg" alt="Stripe" />
          </div>
          <div className="payment-method-text">
            <h3>Pay with Stripe</h3>
            <p>Secure checkout with credit card, debit card, and more</p>
          </div>
        </button>
        
        {/* You can add more payment methods here in the future */}
      </div>
      
      {loading && <div className="loading">Processing your request...</div>}
      {error && <div className="error-message">{error}</div>}
    </div>
  );
};

const PlansSubscription = () => {
  const navigate = useNavigate();
  const [selectedPlan, setSelectedPlan] = useState(null);
  const [loadingPlan, setLoadingPlan] = useState(false);
  const [plans, setPlans] = useState([]);
  const [fetchError, setFetchError] = useState(null);

  // Fetch plans when component mounts
  useEffect(() => {
    fetchPlans();
  }, []);

  // Function to fetch plans from backend
  const fetchPlans = async () => {
    setLoadingPlan(true);
    setFetchError(null);
    
    try {
      // Replace with your actual API endpoint
      const response = await fetch("http://localhost:5000/api/plans");
      
      if (!response.ok) {
        throw new Error("Failed to fetch plans");
      }
      
      const data = await response.json();
      setPlans(data);
    } catch (error) {
      console.error("Error fetching plans:", error);
      setFetchError("");
      
      // Fallback to static plans if fetching fails
      setPlans([
        {
          id: "plan_1month",
          name: "1 Month Plan",
          price: "₱500",
          amount: 500,
          description: "Access premium features for one month.",
        },
        {
          id: "plan_6months",
          name: "6 Months Plan",
          price: "₱2,500",
          amount: 2500,
          description: "Enjoy premium features for six months at a discounted rate.",
        },
        {
          id: "plan_1year",
          name: "1 Year Plan",
          price: "₱4,800",
          amount: 4800,
          description: "Get the best value with a full-year subscription.",
        },
      ]);
    } finally {
      setLoadingPlan(false);
    }
  };

  const handleSelectPlan = async (plan) => {
    setLoadingPlan(true);
    
    try {
      // Fetch the latest plan details before proceeding to checkout
      const response = await fetch(`http://localhost:5000/api/plans/${plan.id}`);
      
      if (!response.ok) {
        throw new Error("Failed to fetch plan details");
      }
      
      const updatedPlan = await response.json();
      setSelectedPlan(updatedPlan);
    } catch (error) {
      console.error("Error fetching plan details:", error);
      // If fetch fails, use the plan data we already have
      setSelectedPlan(plan);
    } finally {
      setLoadingPlan(false);
    }
  };

  const handleCancelPayment = () => {
    setSelectedPlan(null);
  };

  return (
    <div className="plans-container">
      {loadingPlan ? (
        <div className="loading-container">
          <p>Loading plans...</p>
        </div>
      ) : !selectedPlan ? (
        <>
          <h1 className="title">Plans & Subscription</h1>
          {fetchError && <div className="error-message">{fetchError}</div>}
          <div className="plans-grid">
            {plans.map((plan, index) => (
              <div key={plan.id || index} className="plan-card">
                <span className="tag">{index === 2 ? "Best Value" : "For You"}</span>
                <h2>{plan.name}</h2>
                <p className="price">{plan.price} / period</p>
                <p className="description">{plan.description}</p>
                <button 
                  className="subscribe-btn" 
                  onClick={() => handleSelectPlan(plan)}
                >
                  Choose Plan
                </button>
              </div>
            ))}
          </div>
        </>
      ) : (
        <PaymentMethodSelector
          selectedPlan={selectedPlan}
          onCancel={handleCancelPayment}
        />
      )}
    </div>
  );
};

export default PlansSubscription;
import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { loadStripe } from "@stripe/stripe-js";
import { Elements, CardElement, useStripe, useElements } from "@stripe/react-stripe-js";
import "./index.css";

// Using your existing Stripe key from the document
const stripePromise = loadStripe("pk_test_51R1s9U4Ib6pQtdzfvbfx0FVqiANXCaAZMxVY6Nu8Eb0VxRIAncuKQ1DlIaVanRDOaejeavEyangNqFmWfHnG1oXI00Metr4woF");

// CheckoutForm component to handle card input and payment submission
const CheckoutForm = ({ selectedPlan, onSuccess, onCancel }) => {
  const stripe = useStripe();
  const elements = useElements();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [zip, setZip] = useState('');

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!stripe || !elements) {
      return;
    }

    if (!name.trim() || !email.trim()) {
      setError("Please provide both name and email");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // Create a payment intent on your server
      const response = await fetch("http://localhost:5000/create-payment-intent", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          amount: selectedPlan.amount,
          planName: selectedPlan.name,
          planId: selectedPlan.id, // Include plan ID in the request
          customer_name: name,
          customer_email: email
        }),
      });

      if (!response.ok) {
        throw new Error("Failed to create payment intent");
      }

      const data = await response.json();

      // Confirm the card payment
      const result = await stripe.confirmCardPayment(data.clientSecret, {
        payment_method: {
          card: elements.getElement(CardElement),
          billing_details: {
            name: name,
            email: email,
            address: {
              postal_code: zip
            }
          },
        },
        receipt_email: email
      });

      if (result.error) {
        throw new Error(result.error.message);
      } else if (result.paymentIntent.status === 'succeeded') {
        onSuccess(result.paymentIntent);
      }
    } catch (error) {
      console.error("Payment error:", error);
      setError(error.message || "Payment failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const cardElementOptions = {
    style: {
      base: {
        fontSize: '16px',
        color: '#424770',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        '::placeholder': {
          color: '#aab7c4',
        },
      },
      invalid: {
        color: '#9e2146',
      },
    },
    hidePostalCode: true
  };

  return (
    <div className="stripe-checkout-container">
      <h2>Pay with card</h2>
      <div className="selected-plan-summary">
        <h3>Selected Plan: {selectedPlan.name}</h3>
        <p>Price: {selectedPlan.price}</p>
        <p>{selectedPlan.description}</p>
        <button className="change-plan-btn" onClick={onCancel}>Change Plan</button>
      </div>
      
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="full-width-input"
            required
          />
        </div>
        
        <div className="form-group">
          <label>Card information</label>
          <div className="card-element-wrapper">
            <CardElement options={cardElementOptions} />
          </div>
        </div>
        
        <div className="form-group">
          <label htmlFor="name">Cardholder name</label>
          <input
            id="name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Full name on card"
            className="full-width-input"
            required
          />
        </div>
        
        <div className="form-group">
          <label htmlFor="country">Country or region</label>
          <select id="country" className="full-width-input" defaultValue="US">
            <option value="US">United States</option>
            <option value="PH">Philippines</option>
            {/* Add more countries as needed */}
          </select>
        </div>
        
        <div className="form-group">
          <label htmlFor="zip">ZIP</label>
          <input
            id="zip"
            type="text"
            value={zip}
            onChange={(e) => setZip(e.target.value)}
            className="full-width-input"
            required
          />
        </div>
        
        {error && <div className="error-message">{error}</div>}
        
        <button 
          type="submit" 
          disabled={!stripe || loading} 
          className="pay-button"
        >
          {loading ? "Processing..." : `Pay ${selectedPlan.price}`}
        </button>
      </form>
      
      <div className="stripe-footer">
        <p>Powered by <span className="stripe-text">stripe</span></p>
        <div className="footer-links">
          <a href="/terms">Terms</a>
          <a href="/privacy">Privacy</a>
        </div>
      </div>
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

  const handlePaymentSuccess = (paymentIntent) => {
    // Navigate to success page with payment details
    navigate(`/payment-success?payment_id=${paymentIntent.id}&plan=${encodeURIComponent(selectedPlan.name)}&amount=${selectedPlan.amount}&status=succeeded`);
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
        <div className="payment-container">
          <Elements stripe={stripePromise}>
            <CheckoutForm 
              selectedPlan={selectedPlan}
              onSuccess={handlePaymentSuccess}
              onCancel={handleCancelPayment}
            />
          </Elements>
        </div>
      )}
    </div>
  );
};

export default PlansSubscription;
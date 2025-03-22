// server.js
const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const stripe = require('stripe')('sk_test_51R1s9U4Ib6pQtdzfYEqmLkpUBiSsRWx4WJJNPnnMrguQwfvLdkS9xlhnFXPUPmRf0YdzmU1TGJLLBUDkH2Ka3gE4004fuIEAKuy'); // Replace with your actual secret key

const app = express();
const port = process.env.PORT || 5000;

// Middleware
app.use(cors());
app.use(bodyParser.json());

// In-memory plan data (in a real app, you'd use a database)
const plans = [
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
];

// Get all plans
app.get('/api/plans', (req, res) => {
  res.json(plans);
});

// Get a specific plan by ID
app.get('/api/plans/:id', (req, res) => {
  const planId = req.params.id;
  const plan = plans.find(p => p.id === planId);
  
  if (!plan) {
    return res.status(404).json({ error: 'Plan not found' });
  }
  
  res.json(plan);
});

// Create a payment intent
app.post('/create-payment-intent', async (req, res) => {
  try {
    const { amount, planName, planId, customer_name, customer_email } = req.body;
    
    // Validate input
    if (!amount || !planName || !customer_email) {
      return res.status(400).json({ error: 'Missing required fields' });
    }

    // Create a customer in Stripe
    const customer = await stripe.customers.create({
      name: customer_name,
      email: customer_email,
      metadata: {
        planId,
        planName
      }
    });

    // Create a payment intent
    const paymentIntent = await stripe.paymentIntents.create({
      amount: amount * 100, // Convert to cents/smallest currency unit
      currency: 'php',
      customer: customer.id,
      metadata: {
        planId,
        planName
      },
      description: `Subscription for ${planName}`
    });

    res.json({
      clientSecret: paymentIntent.client_secret
    });
  } catch (error) {
    console.error('Error creating payment intent:', error);
    res.status(500).json({ error: error.message });
  }
});

// Handle successful payment webhook (you'll need to set this up in your Stripe dashboard)
app.post('/webhook', bodyParser.raw({ type: 'application/json' }), async (req, res) => {
  const sig = req.headers['stripe-signature'];
  const endpointSecret = 'your_webhook_signing_secret'; // Replace with your webhook signing secret
  
  let event;
  
  try {
    event = stripe.webhooks.constructEvent(req.body, sig, endpointSecret);
  } catch (err) {
    return res.status(400).send(`Webhook Error: ${err.message}`);
  }
  
  // Handle the event
  if (event.type === 'payment_intent.succeeded') {
    const paymentIntent = event.data.object;
    
    
    
    console.log('Payment succeeded:', paymentIntent.id);
  }
  
  res.json({ received: true });
});

app.listen(port, () => {
  console.log(`Server running on port ${port}`);
});
import React, { useEffect, useState } from "react";
import { database } from "../firebase";
import { ref, onValue } from "firebase/database";
import { 
  LineChart, 
  Line, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  Legend, 
  ResponsiveContainer 
} from 'recharts';

const Analytics = () => {
    const [analyticsData, setAnalyticsData] = useState([]);
    const [weeklyAppointments, setWeeklyAppointments] = useState([]);
    const [monthlyAppointments, setMonthlyAppointments] = useState([]);
    const [yearlyAppointments, setYearlyAppointments] = useState([]);
    const [selectedTimeframe, setSelectedTimeframe] = useState("weekly");
    const [lawyerColors, setLawyerColors] = useState({});

    useEffect(() => {
        // Load lawyer usage data
        const analyticsRef = ref(database, "lawyer_usage");
        onValue(analyticsRef, (snapshot) => {
            const data = snapshot.val();
            const analyticsList = data
                ? Object.entries(data).map(([lawyer, details]) => ({
                      lawyer,
                      usageCount: details.usage_count || 0,
                  }))
                : [];
            setAnalyticsData(analyticsList);
        });

        // Load appointment data
        const appointmentsRef = ref(database, "appointments");
        onValue(appointmentsRef, (snapshot) => {
            const data = snapshot.val();
            if (!data) return;

            const appointments = Object.entries(data).map(([id, details]) => ({
                id,
                ...details,
                date: new Date(details.date),
            }));

            processAppointmentData(appointments);
        });
    }, []);

    useEffect(() => {
        // Assign a unique color to each lawyer for the charts
        const uniqueLawyers = new Set();
        
        // Collect all lawyer IDs from the data
        weeklyAppointments.forEach(week => {
            week.lawyerBreakdown.forEach(item => uniqueLawyers.add(item.lawyer));
        });
        
        monthlyAppointments.forEach(month => {
            month.lawyerBreakdown.forEach(item => uniqueLawyers.add(item.lawyer));
        });
        
        yearlyAppointments.forEach(year => {
            year.lawyerBreakdown.forEach(item => uniqueLawyers.add(item.lawyer));
        });
        
        // Generate colors for each lawyer
        const colors = {};
        const colorOptions = [
            '#8884d8', '#82ca9d', '#ffc658', '#ff8042', '#0088FE', 
            '#00C49F', '#FFBB28', '#FF8042', '#a4de6c', '#d0ed57'
        ];
        
        [...uniqueLawyers].forEach((lawyer, index) => {
            colors[lawyer] = colorOptions[index % colorOptions.length];
        });
        
        setLawyerColors(colors);
    }, [weeklyAppointments, monthlyAppointments, yearlyAppointments]);

    const processAppointmentData = (appointments) => {
        // Get current date info
        const now = new Date();
        const currentWeek = getWeekNumber(now);
        const currentMonth = now.getMonth();
        const currentYear = now.getFullYear();

        // Weekly data processing
        const weeklyData = {};
        appointments.forEach(appointment => {
            const appointmentWeek = getWeekNumber(appointment.date);
            const appointmentYear = appointment.date.getFullYear();
            
            // Only include appointments from the current year and up to 10 weeks back
            if (appointmentYear === currentYear && appointmentWeek >= currentWeek - 10 && appointmentWeek <= currentWeek) {
                const weekKey = `Week ${appointmentWeek}`;
                if (!weeklyData[weekKey]) {
                    weeklyData[weekKey] = { count: 0, lawyers: {} };
                }
                weeklyData[weekKey].count++;
                
                // Track by lawyer
                const lawyerId = appointment.lawyerId;
                if (!weeklyData[weekKey].lawyers[lawyerId]) {
                    weeklyData[weekKey].lawyers[lawyerId] = 0;
                }
                weeklyData[weekKey].lawyers[lawyerId]++;
            }
        });
        
        // Convert to array and sort
        const weeklyResult = Object.entries(weeklyData).map(([week, data]) => ({
            period: week,
            count: data.count,
            lawyerBreakdown: Object.entries(data.lawyers).map(([lawyer, count]) => ({
                lawyer,
                count
            }))
        })).sort((a, b) => {
            // Extract week numbers for sorting
            const weekA = parseInt(a.period.replace('Week ', ''));
            const weekB = parseInt(b.period.replace('Week ', ''));
            return weekA - weekB;
        });
        
        setWeeklyAppointments(weeklyResult);

        // Monthly data processing
        const monthlyData = {};
        const monthNames = ["January", "February", "March", "April", "May", "June", 
                           "July", "August", "September", "October", "November", "December"];
                           
        appointments.forEach(appointment => {
            const appointmentMonth = appointment.date.getMonth();
            const appointmentYear = appointment.date.getFullYear();
            
            // Only include appointments from the current year
            if (appointmentYear === currentYear) {
                const monthKey = monthNames[appointmentMonth];
                if (!monthlyData[monthKey]) {
                    monthlyData[monthKey] = { count: 0, lawyers: {} };
                }
                monthlyData[monthKey].count++;
                
                // Track by lawyer
                const lawyerId = appointment.lawyerId;
                if (!monthlyData[monthKey].lawyers[lawyerId]) {
                    monthlyData[monthKey].lawyers[lawyerId] = 0;
                }
                monthlyData[monthKey].lawyers[lawyerId]++;
            }
        });
        
        // Convert to array and sort
        const monthlyResult = Object.entries(monthlyData).map(([month, data]) => ({
            period: month,
            count: data.count,
            lawyerBreakdown: Object.entries(data.lawyers).map(([lawyer, count]) => ({
                lawyer,
                count
            }))
        })).sort((a, b) => {
            // Sort by month index
            return monthNames.indexOf(a.period) - monthNames.indexOf(b.period);
        });
        
        setMonthlyAppointments(monthlyResult);

        // Yearly data processing
        const yearlyData = {};
        appointments.forEach(appointment => {
            const appointmentYear = appointment.date.getFullYear();
            
            // Include a few years back
            if (appointmentYear >= currentYear - 5) {
                const yearKey = appointmentYear.toString();
                if (!yearlyData[yearKey]) {
                    yearlyData[yearKey] = { count: 0, lawyers: {} };
                }
                yearlyData[yearKey].count++;
                
                // Track by lawyer
                const lawyerId = appointment.lawyerId;
                if (!yearlyData[yearKey].lawyers[lawyerId]) {
                    yearlyData[yearKey].lawyers[lawyerId] = 0;
                }
                yearlyData[yearKey].lawyers[lawyerId]++;
            }
        });
        
        // Convert to array and sort
        const yearlyResult = Object.entries(yearlyData).map(([year, data]) => ({
            period: year,
            count: data.count,
            lawyerBreakdown: Object.entries(data.lawyers).map(([lawyer, count]) => ({
                lawyer,
                count
            }))
        })).sort((a, b) => a.period - b.period);
        
        setYearlyAppointments(yearlyResult);
    };

    // Helper function to get week number
    const getWeekNumber = (date) => {
        const firstDayOfYear = new Date(date.getFullYear(), 0, 1);
        const pastDaysOfYear = (date - firstDayOfYear) / 86400000;
        return Math.ceil((pastDaysOfYear + firstDayOfYear.getDay() + 1) / 7);
    };

    // Prepare data for recharts
    const prepareChartData = () => {
        let dataToUse = [];
        
        switch(selectedTimeframe) {
            case "weekly":
                dataToUse = weeklyAppointments;
                break;
            case "monthly":
                dataToUse = monthlyAppointments;
                break;
            case "yearly":
                dataToUse = yearlyAppointments;
                break;
            default:
                dataToUse = weeklyAppointments;
        }
        
        // Transform data for recharts
        // First, get all unique lawyers across the dataset
        const uniqueLawyers = new Set();
        dataToUse.forEach(item => {
            item.lawyerBreakdown.forEach(lawyer => {
                uniqueLawyers.add(lawyer.lawyer);
            });
        });
        
        // Create the chart data structure
        return dataToUse.map(item => {
            const chartItem = {
                name: item.period,
                total: item.count,
            };
            
            // Add a data point for each lawyer
            item.lawyerBreakdown.forEach(lawyer => {
                chartItem[lawyer.lawyer] = lawyer.count;
            });
            
            // Ensure all lawyers have a value (0 if not present)
            uniqueLawyers.forEach(lawyer => {
                if (chartItem[lawyer] === undefined) {
                    chartItem[lawyer] = 0;
                }
            });
            
            return chartItem;
        });
    };

    const renderLineChart = () => {
        const chartData = prepareChartData();
        
        // Get all lawyer IDs for the chart lines
        const lawyerIds = Object.keys(lawyerColors);
        
        return (
            <div className="chart-container" style={{ width: '100%', height: 400 }}>
                <ResponsiveContainer>
                    <LineChart
                        data={chartData}
                        margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
                    >
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis dataKey="name" />
                        <YAxis />
                        <Tooltip />
                        <Legend />
                        <Line 
                            type="monotone" 
                            dataKey="total" 
                            stroke="#000000" 
                            strokeWidth={2} 
                            name="Total Appointments" 
                        />
                        {lawyerIds.map(lawyerId => (
                            <Line 
                                key={lawyerId}
                                type="monotone" 
                                dataKey={lawyerId} 
                                stroke={lawyerColors[lawyerId]}
                                activeDot={{ r: 8 }}
                                name={`Lawyer ${lawyerId}`}
                            />
                        ))}
                    </LineChart>
                </ResponsiveContainer>
            </div>
        );
    };

    const renderCurrentData = () => {
        let dataToRender = [];
        
        switch(selectedTimeframe) {
            case "weekly":
                dataToRender = weeklyAppointments;
                break;
            case "monthly":
                dataToRender = monthlyAppointments;
                break;
            case "yearly":
                dataToRender = yearlyAppointments;
                break;
            default:
                dataToRender = weeklyAppointments;
        }
        
        return (
            <div className="appointment-stats">
                <h4>{selectedTimeframe.charAt(0).toUpperCase() + selectedTimeframe.slice(1)} Appointment Statistics</h4>
                
                {dataToRender.length === 0 ? (
                    <p>No data available for this timeframe.</p>
                ) : (
                    <>
                        {renderLineChart()}
                        
                        <div className="stats-container">
                            {dataToRender.map((item, index) => (
                                <div key={index} className="stat-card">
                                    <h5>{item.period}</h5>
                                    <p><strong>Total Appointments:</strong> {item.count}</p>
                                    <div className="lawyer-breakdown">
                                        <p><strong>By Lawyer:</strong></p>
                                        <ul>
                                            {item.lawyerBreakdown.map((lawyer, idx) => (
                                                <li key={idx} style={{ color: lawyerColors[lawyer.lawyer] }}>
                                                    {lawyer.lawyer}: {lawyer.count} appointments
                                                </li>
                                            ))}
                                        </ul>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </>
                )}
            </div>
        );
    };

    return (
        <div className="analytics-container">
            <h3>Appointments & Analytics</h3>
            
            <div className="lawyer-usage">
                <h4>Overall Lawyer Usage</h4>
                <ul>
                    {analyticsData.map((item, index) => (
                        <li key={index}>
                            {item.lawyer}: {item.usageCount} times used
                        </li>
                    ))}
                </ul>
            </div>
            
            <div className="timeframe-selector">
                <h4>View Appointments By:</h4>
                <div className="button-group">
                    <button 
                        className={selectedTimeframe === "weekly" ? "active" : ""} 
                        onClick={() => setSelectedTimeframe("weekly")}
                    >
                        Weekly
                    </button>
                    <button 
                        className={selectedTimeframe === "monthly" ? "active" : ""} 
                        onClick={() => setSelectedTimeframe("monthly")}
                    >
                        Monthly
                    </button>
                    <button 
                        className={selectedTimeframe === "yearly" ? "active" : ""} 
                        onClick={() => setSelectedTimeframe("yearly")}
                    >
                        Yearly
                    </button>
                </div>
            </div>
            
            {renderCurrentData()}
        </div>
    );
};

export default Analytics;
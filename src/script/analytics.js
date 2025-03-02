import React, { useEffect, useState } from "react";
import { database } from "../firebase";
import { ref, onValue } from "firebase/database";

const Analytics = () => {
    const [analyticsData, setAnalyticsData] = useState([]);

    useEffect(() => {
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
    }, []);

    return (
        <div>
            <h3>Appointments & Analytics</h3>
            <ul>
                {analyticsData.map((item, index) => (
                    <li key={index}>
                        {item.lawyer}: {item.usageCount} times used
                    </li>
                ))}
            </ul>
        </div>
    );
};

export default Analytics;

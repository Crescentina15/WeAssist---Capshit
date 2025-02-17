import { ref, get } from "firebase/database";
import { db } from "./firebase";

export const getLawyersByLawFirm = async (lawFirm) => {
  try {
    const lawyersRef = ref(db, "lawyers");
    const snapshot = await get(lawyersRef);
    const lawyersData = snapshot.val();
    const filteredLawyers = [];

    for (let key in lawyersData) {
      if (lawyersData[key].lawFirm === lawFirm) {
        filteredLawyers.push(lawyersData[key]);
      }
    }
    return filteredLawyers;
  } catch (error) {
    console.error("Error fetching lawyers by lawFirm: ", error);
    return [];
  }
};

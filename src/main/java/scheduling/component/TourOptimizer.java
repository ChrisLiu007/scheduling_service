package scheduling.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scheduling.beans.CalendarAppointment;
import scheduling.beans.GeoPoint;
//import beans.Timeslot;
import scheduling.exceptions.RoutingNotFoundException;
import scheduling.connector.RoutingConnector;
import scheduling.model.PlanningResponse;
import scheduling.utility.DateAnalyser;
import scheduling.utility.MeasureConverter;

@Component
public class TourOptimizer {
	
	@Autowired
	private final RoutingConnector routingConnector;
	
	private List<CalendarAppointment> appointments = new ArrayList<CalendarAppointment>();
	private Date beginWork;
	private Date endWork;
	private Date beginBreak;
	private Date endBreak;
	private GeoPoint beginLocation;
	private GeoPoint endLocation;
	private String calendarId;
	
	public TourOptimizer() {
		this.routingConnector = new RoutingConnector();;		
	}
	
	public TourOptimizer (List<CalendarAppointment> appointments, 
			Date beginWork,
			Date endWork,
			Date beginBreak,
			Date endBreak,
			GeoPoint beginLocation,
			GeoPoint endLocation,
			String calendarId) {
		this.routingConnector = new RoutingConnector();	
		this.appointments = appointments;
		this.beginWork = beginWork;
		this.endWork = endWork;
		this.beginBreak = beginBreak;
		this.endBreak = endBreak;
		this.beginLocation = beginLocation;
		this.endLocation = endLocation;
		this.calendarId = calendarId;
	}

	public List<CalendarAppointment> getAppointments() {
		return appointments;
	}

	public void setAppointments(List<CalendarAppointment> appointments) {
		this.appointments = appointments;
	}

	public Date getBeginWork() {
		return beginWork;
	}

	public void setBeginWork(Date beginWork) {
		this.beginWork = beginWork;
	}

	public Date getEndWork() {
		return endWork;
	}

	public void setEndWork(Date endWork) {
		this.endWork = endWork;
	}
	
	public Date getBeginBreak() {
		return beginBreak;
	}

	public void setBeginBreak(Date beginBreak) {
		this.beginBreak = beginBreak;
	}

	public Date getEndBreak() {
		return endBreak;
	}

	public void setEndBreak(Date endBreak) {
		this.endBreak = endBreak;
	}
	
	public GeoPoint getBeginLocation() {
		return beginLocation;
	}

	public void setBeginLocation(GeoPoint beginLocation) {
		this.beginLocation = beginLocation;
	}
	
	public GeoPoint getEndLocation() {
		return endLocation;
	}

	public void setEndLocation(GeoPoint endLocation) {
		this.endLocation = endLocation;
	}

	public String getCalendarId() {
		return calendarId;
	}
	
	public void setCalendarId(String calendarId) {
		this.calendarId = calendarId;
	}
	
	public boolean checkTimeslotForNewAppointment(CalendarAppointment appointment) {
		
		// check, if it is possible to include appointment in the list
		int durationOfAppointment = DateAnalyser.getDurationBetweenDates(
				appointment.getStartDate(), appointment.getEndDate());
		for(int index = 0; index < appointments.size() - 1; index++) {
			
			CalendarAppointment startAppointment = appointments.get(index);
			CalendarAppointment endAppointment = appointments.get(index + 1);
			int durationBetweenTwoAppointments = DateAnalyser.getDurationBetweenDates(
					startAppointment.getEndDate(), endAppointment.getStartDate());
			if(durationOfAppointment < durationBetweenTwoAppointments) {
				return true;
			}
			
		}
		return false;
		
	}

	public List<PlanningResponse> getPossibleTimeslotForNewAppointment(
			GeoPoint appointmentLocation, 
			int durationOfAppointmentInMin
			) throws Exception {
		
		// return a valid starting date for the new appointment	
		TreeMap<Integer,Integer> timeIndexMapping = Maps.newTreeMap();
		HashMap<Integer,Double> saveDistanceForTravelTime = Maps.newHashMap();
		HashMap<Integer,Integer> saveTravelTimesBefore = Maps.newHashMap();
		HashMap<Integer,Integer> saveTravelTimesAfter = Maps.newHashMap();

		// perform optimization on a copy of the appointment list adding start and end as appointments
		List<CalendarAppointment> tempAppointments = Lists.newArrayList();
		if (beginLocation != null && beginWork != null)
			tempAppointments.add(new CalendarAppointment(beginLocation, beginWork, beginWork, calendarId));
		
		for (CalendarAppointment app : appointments)
			if (app.getEndDate().after(beginWork))
				tempAppointments.add(app);		
		
		if (endLocation != null && endWork != null)
			tempAppointments.add(new CalendarAppointment(endLocation, endWork, endWork, calendarId));
		
		// calculate duration of break
		int durationBreak = DateAnalyser.getDurationBetweenDates(
				beginBreak, endBreak);
		/*
		// check, if it is possible to put the new appointment at the beginning or end
		Date latestEndDate = appointments.get(0).getStartDate();
		Date latestStartEndDate = appointments.get(appointments.size() - 1).getEndDate();
		
		int durationBetweenBeginningAndAppointment = DateAnalyser.
				getDurationBetweenDates(beginWork, latestEndDate);
		
		int durationBetweenEndAndAppointment = DateAnalyser.
				getDurationBetweenDates(latestStartEndDate, endWork);
		
		if(durationBetweenBeginningAndAppointment > durationOfAppointmentInMin) {
			int travelTimeInMinutes = MeasureConverter.getTimeInMinutes(
					RoutingConnector.getTravelTime(appointmentLocation, appointments.get(0).getPosition()));
			// add the travel time and create a new time slot
			if((durationBetweenBeginningAndAppointment - travelTimeInMinutes)
					> durationOfAppointmentInMin) {
				timeslots.add(new Timeslot(beginningDate, 
						DateAnalyser.getLatestPossibleEndDate(latestEndDate, 
								travelTimeInMinutes, false)),
						calendarID);
			}
		}
		// try to insert the appoint at the end
		if (durationBetweenEndAndAppointment > durationOfAppointmentInMin) {
			int travelTimeInMinutes = MeasureConverter.getTimeInMinutes(
					RoutingConnector.getTravelTime(appointments.get(appointments.size() - 1).getPosition(), 
							appointmentLocation));
			// add the travel time and create a new time slot
			if((durationBetweenEndAndAppointment - travelTimeInMinutes)
					> durationOfAppointmentInMin) {
				timeslots.add(new Timeslot(DateAnalyser.getEarliestPossibleStartingDate(
						latestStartEndDate, travelTimeInMinutes, false), endWork));
			}
		}*/
		
		// find insertion position
		for(int index = 0; index <= tempAppointments.size() - 2; index++) {
			CalendarAppointment startAppointment = tempAppointments.get(index);
			CalendarAppointment endAppointment = tempAppointments.get(index + 1);
			int durationBetweenTwoAppointments = DateAnalyser.getDurationBetweenDates(
					startAppointment.getEndDate(), endAppointment.getStartDate());
			
			// check, if appointment duration is smaller than time between appointments
			if(durationOfAppointmentInMin >= durationBetweenTwoAppointments) {
				continue;
			}
						
			try {
				int travelTimeInMinutesBefore = MeasureConverter.getTimeInMinutes(
						routingConnector.getTravelTime(startAppointment.getPosition(), appointmentLocation));
				int travelTimeInMinutesAfter = MeasureConverter.getTimeInMinutes(
						routingConnector.getTravelTime(appointmentLocation, endAppointment.getPosition()));
			
				int additionalWorkTime = durationOfAppointmentInMin + travelTimeInMinutesBefore + travelTimeInMinutesAfter;
				
				// reduce durationBetweenTwoAppointments if the start time of the break
				// lies after the first appointment but before the next appointment
				if (startAppointment.getStartDate().before(beginBreak) &&
						endAppointment.getStartDate().after(beginBreak))
					durationBetweenTwoAppointments -= durationBreak;
				
				if(additionalWorkTime < durationBetweenTwoAppointments) {
					// calculate travel time of the whole route
					List<CalendarAppointment> newAppointments = Lists.newArrayList(tempAppointments);
					newAppointments.add(index + 1, new CalendarAppointment(appointmentLocation, null, null, null));
					
					double calculateDistance = calculateDistance(newAppointments);
					int calculateTravelTimes = calculateTravelTimes(newAppointments);
					// check, if time is the same and eventually override the tree map
					if(!timeIndexMapping.containsKey(calculateTravelTimes)) {
						timeIndexMapping.put(calculateTravelTimes, index);
						// save travel times for calculation
						saveTravelTimesBefore.put(index, travelTimeInMinutesBefore);
						saveTravelTimesAfter.put(index, travelTimeInMinutesAfter);
						// save distance and travel time
						saveDistanceForTravelTime.put(calculateTravelTimes, calculateDistance);
					} else {
						// check, if we have to override the old value
						if(saveDistanceForTravelTime.get(calculateTravelTimes) > calculateDistance) {
							// update data structure and prioritize shorter route
							timeIndexMapping.put(calculateTravelTimes, index);
							// save travel times for calculation
							saveTravelTimesBefore.put(index, travelTimeInMinutesBefore);
							saveTravelTimesAfter.put(index, travelTimeInMinutesAfter);
							// save distance and travel time
							saveDistanceForTravelTime.put(calculateTravelTimes, calculateDistance);
						}
					}
				}
			} catch (RoutingNotFoundException routEx) {
				throw new RoutingNotFoundException("Routing not possible!");
			}
		}
		
		// extract the start date of new appointment
		if(saveTravelTimesBefore.size() > 0 
				&& timeIndexMapping.size() > 0
				&& saveTravelTimesAfter.size() > 0) {
			// the first value is the lowest travel time
			NavigableSet<Integer> descendingKeySet = timeIndexMapping.descendingKeySet().descendingSet();
			List<PlanningResponse> timeslots = Lists.newLinkedList();
			// add all possible time slots
			// first one is the possible time slot with the best travel time
			for(int index: descendingKeySet) {
				int valueIndex = timeIndexMapping.get(index);
				timeslots.add(
						new PlanningResponse(index, saveDistanceForTravelTime.get(index), //new Timeslot(
								DateAnalyser.getEarliestPossibleStartingDate(
										tempAppointments.get(valueIndex).getEndDate(), 
										saveTravelTimesBefore.get(valueIndex).intValue(), false
										), 
								DateAnalyser.getLatestPossibleEndDate(
										tempAppointments.get(valueIndex + 1).getStartDate(), 
										saveTravelTimesAfter.get(valueIndex).intValue(), false
										)
								//)
								,calendarId
								));
			}
			return timeslots;
		}
		// return empty list, if there are no valid appointments 
		return Lists.newLinkedList();
		
	}
	
	private double calculateDistance(List<CalendarAppointment> newAppointments) throws Exception {
		
		double travelDistanceSum = 0.0;
		for(int index = 0; index < newAppointments.size() - 2; index++) {
			travelDistanceSum += routingConnector.getTravelDistance(newAppointments.get(index).getPosition(), 
							newAppointments.get(index + 1).getPosition());
		}
		return travelDistanceSum;
	}

	public int calculateTravelTimes(List<CalendarAppointment> newAppointments) throws Exception {
		
		int travelTimeSum = 0;
		for(int index = 0; index < newAppointments.size() - 2; index++) {
			travelTimeSum += MeasureConverter.getTimeInMinutes(
					routingConnector.getTravelTime(newAppointments.get(index).getPosition(), 
							newAppointments.get(index + 1).getPosition()));
		}
		return travelTimeSum;
		
	}
	
	public List<Double[]> getOptimalRoute(GeoPoint[] points) throws Exception {
		
		int travelTime = 0;
		Integer[] bestOrder = new Integer[points.length];
		Integer[] order = new Integer[points.length];
		
		for (int i = 0; i < order.length; i++)
			order[i] = i;
		
		Permutations<Integer> perm = new Permutations<Integer>(order);
	    int count = 0;
	    while(perm.hasNext()){
	    	Integer[] nextOrder = perm.next();
	    	int permTime = calculateTravelTimes(shufflePoints(points, nextOrder));
	    	
	    	if (travelTime == 0 || permTime < travelTime) {
	    		travelTime = permTime;
	    		bestOrder = Arrays.copyOf(nextOrder, nextOrder.length);
	    	}
	        count++;
	    }
	    System.out.println("total: " + count);
	    System.out.println("Best time: " + travelTime);
	    System.out.println("Best combination: " + Arrays.toString(bestOrder));
	    
	    return routingConnector.getRoute(shufflePoints(points, bestOrder));
	}
	
	public GeoPoint[] shufflePoints(GeoPoint[] points, Integer[] order) {
		/*
		GeoPoint[] shuffledPoints = new GeoPoint[points.length];
		
		for (int i = 0; i < order.length; i++)
			shuffledPoints[i] = points[order[i]];
		
		return shuffledPoints;
		*/
		
		GeoPoint[] shuffledPoints = new GeoPoint[points.length+2];
		
		shuffledPoints[0] = new GeoPoint(51.0405489,13.6575849502255);
		
		for (int i = 0; i < order.length; i++)
			shuffledPoints[i+1] = points[order[i]];
		
		shuffledPoints[points.length+1] = new GeoPoint(51.0405489,13.6575849502255);
		
		return shuffledPoints;
	}
	
	public int calculateTravelTimes(GeoPoint[] points) throws Exception {
		
		int travelTimeSum = 0;
		for(int index = 0; index < points.length - 1; index++) {
			travelTimeSum += MeasureConverter.getTimeInMinutes(
					routingConnector.getTravelTime(points[index],points[index + 1]));
		}
		return travelTimeSum;
	}
	
	public double calculateTravelDistances(GeoPoint[] points) throws Exception {
		
		int travelDistanceSum = 0;
		for(int index = 0; index < points.length - 1; index++) {
			travelDistanceSum += routingConnector.getTravelDistance(points[index],points[index + 1]);
		}
		return travelDistanceSum;
	}
	
}
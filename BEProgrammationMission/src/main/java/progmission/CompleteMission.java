package progmission;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import fr.cnes.sirius.patrius.assembly.models.SensorModel;
import fr.cnes.sirius.patrius.attitudes.Attitude;
import fr.cnes.sirius.patrius.attitudes.AttitudeLaw;
import fr.cnes.sirius.patrius.attitudes.AttitudeLawLeg;
import fr.cnes.sirius.patrius.attitudes.AttitudeLeg;
import fr.cnes.sirius.patrius.attitudes.AttitudeProvider;
import fr.cnes.sirius.patrius.attitudes.ConstantSpinSlew;
import fr.cnes.sirius.patrius.attitudes.StrictAttitudeLegsSequence;
import fr.cnes.sirius.patrius.attitudes.TargetGroundPointing;
import fr.cnes.sirius.patrius.events.CodedEvent;
import fr.cnes.sirius.patrius.events.CodedEventsLogger;
import fr.cnes.sirius.patrius.events.GenericCodingEventDetector;
import fr.cnes.sirius.patrius.events.Phenomenon;
import fr.cnes.sirius.patrius.events.postprocessing.AndCriterion;
import fr.cnes.sirius.patrius.events.postprocessing.ElementTypeFilter;
import fr.cnes.sirius.patrius.events.postprocessing.NotCriterion;
import fr.cnes.sirius.patrius.events.postprocessing.Timeline;
import fr.cnes.sirius.patrius.events.sensor.SensorVisibilityDetector;
import fr.cnes.sirius.patrius.frames.FramesFactory;
import fr.cnes.sirius.patrius.frames.TopocentricFrame;
import fr.cnes.sirius.patrius.frames.transformations.Transform;
import fr.cnes.sirius.patrius.math.geometry.euclidean.threed.Vector3D;
import fr.cnes.sirius.patrius.math.util.MathLib;
import fr.cnes.sirius.patrius.orbits.pvcoordinates.PVCoordinates;
import fr.cnes.sirius.patrius.orbits.pvcoordinates.PVCoordinatesProvider;
import fr.cnes.sirius.patrius.propagation.analytical.KeplerianPropagator;
import fr.cnes.sirius.patrius.propagation.events.ConstantRadiusProvider;
import fr.cnes.sirius.patrius.propagation.events.EventDetector;
import fr.cnes.sirius.patrius.propagation.events.LocalRadiusProvider;
import fr.cnes.sirius.patrius.propagation.events.ThreeBodiesAngleDetector;
import fr.cnes.sirius.patrius.time.AbsoluteDate;
import fr.cnes.sirius.patrius.time.AbsoluteDateInterval;
import fr.cnes.sirius.patrius.time.AbsoluteDateIntervalsList;
import fr.cnes.sirius.patrius.utils.exception.PatriusException;
import reader.Site;
import utils.ConstantsBE;
import utils.LogUtils;
import utils.ProjectUtils;

/**
 * This class implements the context of an Earth Observation mission.
 *
 * @author herberl
 */
public class CompleteMission extends SimpleMission {

	/**
	 * Logger for this class.
	 */
	private final Logger logger = LogUtils.GLOBAL_LOGGER;

	/**
	 * Maximum checking interval (s) for the event detection during the orbit
	 * propagation.
	 */
	public static final double MAXCHECK_EVENTS = 120.0;

	/**
	 * Default convergence threshold (s) for the event computation during the orbit
	 * propagation.
	 */
	public static final double TRESHOLD_EVENTS = 1.e-4;

	/**
	 * This {@link Map} will be used to enumerate each site access {@link Timeline},
	 * that is to say a {@link Timeline} with access windows respecting all
	 * observation conditions. This object corresponds to the access plan, which
	 * will be computed in the computeAccessPlan() method.
	 */
	private final Map<Site, Timeline> accessPlan;

	/**
	 * This {@link Map} will be used to enumerate each site's programmed
	 * observation. We suggest to use an {@link AttitudeLawLeg} to encapsulate the
	 * guidance law of each observation. This object corresponds to the observation
	 * plan, which will be computed in the computeObservationPlan() method.
	 */
	private final Map<Site, AttitudeLawLeg> observationPlan;

	/**
	 * {@link StrictAttitudeLegsSequence} representing the cinematic plan during the
	 * whole mission horizon. Each {@link AttitudeLeg} corresponds to a diffrent
	 * attitude law : either nadir pointing, target pointing or a slew between two
	 * laws. This object corresponds to the cinematic plan, which will be computed
	 * in the computeCinematicPlan() method.
	 */
	private final StrictAttitudeLegsSequence<AttitudeLeg> cinematicPlan;

	/**
	 * Constructor for the {@link CompleteMission} class.
	 *
	 * @param missionName   Name of the mission
	 * @param numberOfSites Number of target {@link Site} to consider, please give a
	 *                      number between 1 and 100.
	 * @throws PatriusException      Can be raised by Patrius when building
	 *                               particular objects. Here it comes from
	 *                               {@link FramesFactory}
	 * @throws IllegalStateException if the mission horizon is too short
	 */
	public CompleteMission(final String missionName, int numberOfSites) throws PatriusException {

		// Since this class extends the SimpleMission class, we need to use the super
		// constructor to instantiate our instance of CompleteMission. All the
		// attributes of the super class will be instantiated during this step.
		super(missionName, numberOfSites);

		// Initialize the mission plans with empty maps. You will fill those HashMaps in
		// the "compute****Plan()" methods.
		this.accessPlan = new HashMap<>();
		this.observationPlan = new HashMap<>();
		this.cinematicPlan = new StrictAttitudeLegsSequence<>();

	}

	/**
	 * A hash based on all the constants of the BE. This hash is unique and ensures
	 * we get the right filename for a given {@link Site} and a given set of
	 * {@link ConstantsBE} parameters. It is used in the names of the files
	 * containing the serialized accesses for all {@link Site}.
	 *
	 */
	private final static int hashConstantBE;

	/**
	 * [DO NOT MODIFY THIS METHOD]
	 * 
	 * This block of code is static, shared by all instances of CompleteMission. It
	 * is executed only once when the class is first instantiated, to produce an
	 * unique hash which encodes the whole constants of the BE in one single code.
	 * You don't have to (and must not) modify this code if you want to benefit from
	 * automatic serialization and reading of you access Timelines.
	 */
	static {
		// Creating an unique hash code to be used for serialization
		int hash = 17;

		long doubleBits;
		int doubleHash;

		// The hash code depends from all the parameters from a given simulation : start
		// and end date and all the ConstantBE values. We build a hashcode from all
		// those values converting them as int

		// Start and end dates
		hash = 31 * hash + ConstantsBE.START_DATE.hashCode();
		hash = 31 * hash + ConstantsBE.END_DATE.hashCode();

		// ConstantBE values
		double doubles[] = new double[] { ConstantsBE.ALTITUDE, ConstantsBE.INCLINATION, ConstantsBE.MEAN_ECCENTRICITY,
				ConstantsBE.ASCENDING_NODE_LONGITUDE, ConstantsBE.POINTING_CAPACITY, ConstantsBE.SPACECRAFT_MASS,
				ConstantsBE.MAX_SUN_INCIDENCE_ANGLE, ConstantsBE.MAX_SUN_PHASE_ANGLE, ConstantsBE.INTEGRATION_TIME,
				ConstantsBE.POINTING_AGILITY_DURATIONS[0], ConstantsBE.POINTING_AGILITY_DURATIONS[1],
				ConstantsBE.POINTING_AGILITY_DURATIONS[2], ConstantsBE.POINTING_AGILITY_DURATIONS[3],
				ConstantsBE.POINTING_AGILITY_DURATIONS[4], ConstantsBE.POINTING_AGILITY_ROTATIONS[0],
				ConstantsBE.POINTING_AGILITY_ROTATIONS[1], ConstantsBE.POINTING_AGILITY_ROTATIONS[2],
				ConstantsBE.POINTING_AGILITY_ROTATIONS[3], ConstantsBE.POINTING_AGILITY_ROTATIONS[4], MAXCHECK_EVENTS,
				TRESHOLD_EVENTS };
		for (Double d : doubles) {
			doubleBits = Double.doubleToLongBits(d);
			doubleHash = (int) (doubleBits ^ (doubleBits >>> 32));
			hash = 31 * hash + doubleHash;
		}

		// Finally assigning the current simulation hashConstantBE value
		hashConstantBE = hash;
	}

	/**
	 * This method should compute the input {@link Site}'s access {@link Timeline}.
	 * That is to say the {@link Timeline} which contains all the {@link Phenomenon}
	 * respecting the access conditions for this site : good visibility + corrrect
	 * illumination of the {@link Site}.
	 * 
	 * For that, we suggest you create as many {@link Timeline} as you need and
	 * combine them with logical gates to filter only the access windows phenomenon.
	 * 
	 * @param targetSite Input target {@link Site}
	 * @return The {@link Timeline} of all the access {@link Phenomenon} for the
	 *         input {@link Site}.
	 * @throws PatriusException If a {@link PatriusException} occurs.
	 */
	private Timeline createSiteAccessTimeline(Site targetSite) throws PatriusException {
		
		// Creating the global timeline, which takes into account the 3 constraints.
		Timeline siteAccessTimeline = createSiteGlobalTimeline(targetSite);

		// First criterion : we create a new phenomenon including these two conditions : satellite visibility and illumination
		final AndCriterion andCriterion = new AndCriterion("Satellite visibility", "Illumination",
				"Satellite visibility and illumination", "Comment about this phenomenon");
		
		// Applying our first criterion in order to add all the new phenonmena inside the global timeline
		andCriterion.applyTo(siteAccessTimeline);
		
		// Second criterion : we create a new phenomenon including this condition : no dazzling
		final NotCriterion notCriterion = new NotCriterion("Dazzling", "No dazzling", "Comment") ;
		
		// Applying our second criterion in order to add all the new phenonmena inside the global timeline		
		notCriterion.applyTo(siteAccessTimeline);
		
		// Third criterion : we create a new phenomenon including these three conditions : satellite visibility and illumination and no dazzling
		final AndCriterion andCriterion2 = new AndCriterion("Satellite visibility and illumination", "No dazzling",
				"Satellite visibility and illumination and no dazzling", "Comment about this phenomenon");
		
		// Applying our third criterion in order to add all the new phenonmena inside the global timeline	
		andCriterion2.applyTo(siteAccessTimeline);

		// Then, creating an ElementTypeFilter that will only keep the phenomenon "Satellite visibility and illumination and no dazzling"
		final ElementTypeFilter obsConditionFilter = new ElementTypeFilter(
				"Satellite visibility and illumination and no dazzling", false);
		
		// Finally, we filter the global timeline to keep only the phenomenon "Satellite visibility and illumination and no dazzling" : this is 
		// our final timeline.
		obsConditionFilter.applyTo(siteAccessTimeline);
		
		// We log the final access timeline associated to the current target
		logger.info("\n" + targetSite.getName());
		ProjectUtils.printTimeline(siteAccessTimeline);

		return siteAccessTimeline;
	}
	
	/**
	 * This method compute a {@link Timeline} object which encapsulates all
	 * the {@link Phenomenon} corresponding to the following phenomena : 
	 * satellite visibility, illumination and dazzling, relative to
	 * the input target {@link Site}.
	 * 
	 * @param targetSite Input target {@link Site}
	 * @return The {@link Timeline} containing all the {@link Phenomenon} relative
	 *         to these 3 phenomena.
	 * @throws PatriusException If a {@link PatriusException} occurs when creating
	 *                          the {@link Timeline}.
	 */
	private Timeline createSiteGlobalTimeline(Site targetSite) throws PatriusException {
		/**
		 * We decided to directly calculate a global timeline which encapsulates the 3 phenomena, 
		 * in order to propagate only once per Site, with the three detectors attached to the 
		 * propagator. We did this to gain computation time.
		 */
		
		// Taking a new local propagator for the site, in order to gain computation time.
		final KeplerianPropagator localPropagator = this.createDefaultPropagator();
		
		//////////////////// VISIBILITY DETECTOR ///////////////////////////////
		
		// Creating the visibility detector and adding it to the propagator.
		final EventDetector constraintVisibilityDetector = createConstraintVisibilityDetector(targetSite);
		localPropagator.addEventDetector(constraintVisibilityDetector);
		
		// Creating the associated CodedEventLogger and plugging it to the visibility detector. 
		final GenericCodingEventDetector codingEventVisibilityDetector = new GenericCodingEventDetector(constraintVisibilityDetector,
				"Start of satellite visibility", "End of satellite visibility", true, "Satellite visibility");
		final CodedEventsLogger eventVisibilityLogger = new CodedEventsLogger();
		final EventDetector eventVisibilityDetector = eventVisibilityLogger.monitorDetector(codingEventVisibilityDetector);
		
		// Adding the logger to the propagator, in order to monitor the event coded by the codingEventDetector
		localPropagator.addEventDetector(eventVisibilityDetector);
		
		//////////////////// ILLUMINATION DETECTOR ///////////////////////////////
		
		// Creating the illumination detector and adding it to the propagator.
		final EventDetector constraintIlluminationDetector = createConstraintIlluminationDetector(targetSite);
		localPropagator.addEventDetector(constraintIlluminationDetector);

		// Creating the associated CodedEventLogger and plugging it to the illumination detector. 
		final GenericCodingEventDetector codingEventIlluminationDetector = new GenericCodingEventDetector(constraintIlluminationDetector,
				"Start of illumination", "End of illumination", true, "Illumination");
		final CodedEventsLogger eventIlluminationLogger = new CodedEventsLogger();
		final EventDetector eventIlluminationDetector = eventIlluminationLogger.monitorDetector(codingEventIlluminationDetector);
		
		// Adding the logger to the propagator, in order to monitor the event coded by the codingEventDetector
		localPropagator.addEventDetector(eventIlluminationDetector);

		//////////////////// DAZZLING DETECTOR ///////////////////////////////
		
		// Creating the dazzling detector and adding it to the propagator.
		final EventDetector constraintDazzlingDetector = createConstraintDazzlingDetector(targetSite);
		localPropagator.addEventDetector(constraintDazzlingDetector);

		// Creating the associated CodedEventLogger and plugging it to the dazzling detector. 
		final GenericCodingEventDetector codingEventDazzlingDetector = new GenericCodingEventDetector(constraintDazzlingDetector,
				"Start of dazzling", "End of dazzling", true, "Dazzling");
		final CodedEventsLogger eventDazzlingLogger = new CodedEventsLogger();
		final EventDetector eventDazzlingDetector = eventDazzlingLogger.monitorDetector(codingEventDazzlingDetector);
		
		// Adding the logger to the propagator, in order to monitor the event coded by the codingEventDetector
		localPropagator.addEventDetector(eventDazzlingDetector);

		//////////////////// ORBIT PROPAGATION ///////////////////////////////
		// Now, the local propagator is configured with all the detectors and loggers. So we can propagate.
		
		// Finally propagating the orbit
		localPropagator.propagate(this.getStartDate(), this.getEndDate());
		
		//////////////////// CREATION OF THE 3 TIMELINES ///////////////////////////////
		
		// Creating the first timeline, which corresponds to the visibility phenomenon
		final Timeline timelineVisibility = new Timeline(eventVisibilityLogger,
		new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()), null);
		
		// Creating the second timeline, which corresponds to the illumination phenomenon
		final Timeline timelineIllumination = new Timeline(eventIlluminationLogger,
				new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()), null);
		
		// Creating the third timeline, which corresponds to the dazzling phenomenon
		final Timeline timelineDazzling = new Timeline(eventDazzlingLogger,
				new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()), null); 
		
		///////////////////// CREATION OF THE GLOBAL TIMELINE /////////////////////////
		// The idea is to create a global timeline concatenating the 3 timelines.

		// Creating the global timeline
		final Timeline siteAccessTimeline = new Timeline(
				new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()));
		
		// Adding the phenomena of all the considered timelines
		for (final Phenomenon phenom : timelineVisibility.getPhenomenaList()) {
			siteAccessTimeline.addPhenomenon(phenom);
		}
		for (final Phenomenon phenom : timelineIllumination.getPhenomenaList()) {
			siteAccessTimeline.addPhenomenon(phenom);
		}
		for (final Phenomenon phenom : timelineDazzling.getPhenomenaList()) {
			siteAccessTimeline.addPhenomenon(phenom); 
		} 

		return siteAccessTimeline;

	}
	
	/**
	 * Create an adapted instance of {@link EventDetector} for monitoring 
	 * the events defined by the visibility constraint, for the input 
	 * target site.
	 * 
	 * @param targetSite Input target {@link Site}
	 * @return An {@link EventDetector} answering the visibility constraint.
	 */
	private EventDetector createConstraintVisibilityDetector(Site targetSite) {
		
		// Creating a sensor model by using the sensor of the satellite
		SensorModel sensorModel = new SensorModel(this.getSatellite().getAssembly(), "sensor");
		
		// Adding the earth as a masking body to the sensor model
		sensorModel.addMaskingCelestialBody(this.getEarth());
		
		// Defining the target by creating a PVCoordinatesProvider and a radius
		PVCoordinatesProvider target = new TopocentricFrame(this.getEarth(), targetSite.getPoint(),targetSite.getName());
		LocalRadiusProvider radius = new ConstantRadiusProvider(0); // radius of 0 because we consider the target as a point.
		
		// Adding the target to the sensor model.
		sensorModel.setMainTarget(target, radius);
		
		// Creating the event visibility detector for the associated target, by using the sensor model.
		EventDetector visibilityDetector = new SensorVisibilityDetector(sensorModel, MAXCHECK_EVENTS, TRESHOLD_EVENTS, 
				EventDetector.Action.CONTINUE, EventDetector.Action.CONTINUE);

		return visibilityDetector;
	}
	
	/**
	 * Create an adapted instance of {@link EventDetector} for monitoring 
	 * the events defined by the illumination constraint, for the input 
	 * target site.
	 * 
	 * @param targetSite Input target {@link Site}
	 * @return An {@link EventDetector} answering the illumination constraint.
	 */
	private EventDetector createConstraintIlluminationDetector(Site targetSite) {
		
		// Defining the target as a PVCoordinatesProvider
		PVCoordinatesProvider target = new TopocentricFrame(this.getEarth(), targetSite.getPoint(),targetSite.getName());
		
		// Creating the illumination detector for the associated target. The angle to consider is the one between the
		// target zenith and the direction target-->sun (sun incidence angle). By taking in input the Earth, the target 
		// and the Sun as we did, the calculated angle is the one between the opposite of the zenith and the direction 
		// target--sun. That's why we compare it with 180 minus the sun incidence angle.
		EventDetector illuminationDetector = new ThreeBodiesAngleDetector(this.getEarth(), target ,
				this.getSun(), MathLib.toRadians(180-ConstantsBE.MAX_SUN_INCIDENCE_ANGLE), MAXCHECK_EVENTS, 
				TRESHOLD_EVENTS, EventDetector.Action.CONTINUE );
		
		return illuminationDetector;
	}
	
	/**
	 * Create an adapted instance of {@link EventDetector} for monitoring 
	 * the events defined by the dazzling, for the input target site.
	 * 
	 * @param targetSite Input target {@link Site}
	 * @return An {@link EventDetector} detecting the dazzling.
	 */
	private EventDetector createConstraintDazzlingDetector(Site targetSite) {
		
		// Defining the target as a PVCoordinatesProvider
		PVCoordinatesProvider target = new TopocentricFrame(this.getEarth(), targetSite.getPoint(),targetSite.getName());
		
		// Creating the dazzling detector for the associated target. The angle to consider is the one between the
		// direction target-->satellite and the direction target--sun (sun phase angle). By taking in input the 
		// satellite, the target and the sun, the calculated angle is also the sun phase angle, so we can compare
		// them.
		EventDetector dazzlingDetector = new ThreeBodiesAngleDetector(this.getSatellite().getPropagator().getPvProvider(), target, 
				this.getSun(), MathLib.toRadians(ConstantsBE.MAX_SUN_PHASE_ANGLE), MAXCHECK_EVENTS, TRESHOLD_EVENTS,
				EventDetector.Action.CONTINUE );
		
		return dazzlingDetector;
	}

	/**
	 * Compute the access plan.
	 * 
	 * Reminder : the access plan corresponds to the object gathering all the
	 * opportunities of access for all the sites of interest during the mission
	 * horizon. One opportunity of access is defined by an access window (an
	 * interval of time during which the satellite can observe the target and during
	 * which all the observation conditions are achieved : visibility, incidence
	 * angle, illumination of the scene,etc.).
	 * @return the sites access plan with one {@link Timeline} per {@link Site}
	 * @throws PatriusException If a {@link PatriusException} occurs during the
	 *                          computations
	 */
	public Map<Site, Timeline> computeAccessPlan() throws PatriusException {
		logger.info("============= Computing Access Plan =============");
		
		// Iterating over all sites
		for (Site targetSite : this.getSiteList()) {
			
			logger.info(" Site : " + targetSite.getName());
			
			// Checking if the Site access Timeline has already been serialized or not
			final String filename = generateSerializationName(targetSite, hashConstantBE);
			File file = new File(filename);
			boolean loaded = false;
	
			// If the file exists for the current Site, we try to load its content
			if (file.exists()) {
				try {
					// Load the timeline from the file and add it to the accessPlan
					final Timeline siteAccessTimeline = loadSiteAccessTimeline(filename);
					this.accessPlan.put(targetSite, siteAccessTimeline);
					ProjectUtils.printTimeline(siteAccessTimeline);
					loaded = true; // the Site has been loaded, no need to compute the access again
					logger.info(filename + "has been loaded successfully!");
				} catch (ClassNotFoundException | IOException e) {
					logger.warn(filename + " could not be loaded !");
					logger.warn(e.getMessage());
				}
			}
			// If it was not serialized or if loading has failed, we need to compute and
			// serialize the site access Timeline
			if (!loaded) {
				logger.info(targetSite.getName() + " has not been serialized, launching access computation...");
				// Computing the site access Timeline
				final Timeline siteAccessTimeline = createSiteAccessTimeline(targetSite);
				this.accessPlan.put(targetSite, siteAccessTimeline);
				ProjectUtils.printTimeline(siteAccessTimeline);
	
				try {
					// Serialize the timeline for later reading
					serializeSiteAccessTimeline(filename, siteAccessTimeline);
					logger.info(filename + " has been serialized successfully !");
				} catch (IOException e) {
					logger.warn(filename + " could not be serialized !");
					logger.warn(e.getMessage());
				}
			}
		}

		return this.accessPlan;
	}

	/**
	 * Compute the observation plan.
	 * 
	 * Reminder : the observation plan corresponds to the sequence of observations
	 * programmed for the satellite during the mission horizon. Each observation is
	 * defined by an observation window (start date; end date defining an
	 * {@link AbsoluteDateInterval}), a target (target {@link Site}) and an
	 * {@link AttitudeLawLeg} giving the attitude guidance to observe the target.
	 * 
	 * @return the sites observation plan with one {@link AttitudeLawLeg} per
	 *         {@link Site}
	 * @throws PatriusException If a {@link PatriusException} occurs during the
	 *                          computations
	 */
	public Map<Site, AttitudeLawLeg> computeObservationPlan() throws PatriusException {
		/**
		 * In order to calculate the observation, we defined a greedy algorithm. Our
		 * algorithm can be decomposed in two steps:
		 * 
		 * Step 1 : defining all the possible observation windows, for all sites,
		 * 			and sorting them by score.
		 * 
		 * 			To do this, we create a global array that contains all the
		 * 			possible observations, for all sites, and for each timeline
		 * 			of each site. In the array, an observation is defined by
		 * 			the association of a Site, an AttitudeLawLeg and a score.
		 * 			Therefore, there might be several times the same Site in 
		 * 			the array, but associated to different observations. 
		 * 			Then, we sort this array by score (in descending order).
		 * 			By doing this, the Sites can be in disorder in the array,
		 * 			because only sort by score. For example, we can have a 
		 * 			sequence :
		 * 			[..., (SiteA, ObsLeg1, Score), (SiteB, ObsLeg1, Score),
		 * 			(SiteA, ObsLeg2, Score), ...].
		 * 			This ensures that we insert each time the best possible 
		 *			observation.
		 * 
		 * Step 2 : inserting observations in the plan, by prioritizing the ones
		 * 			with the highest scores.
		 * 
		 * 			To do this, we iterate over the observations defined at the
		 * 			previous step. For each iteration, we test if the
		 * 			observation can be inserted in the plan.
		 * 			First condition : the Site shouldn't have been already
		 * 			observed.
		 * 			Second condition : there shouldn't be another observation
		 * 			in the same time interval.
		 * 			Third condition : the free-interval with the previous
		 * 			observation and the next one should be large enough to
		 * 			perform the slew.
		 * 			If one of these conditions is not respected, we try to
		 * 			insert the next observation in the array...etc... until
		 * 			we reach the end of the array.
		 */
		
		logger.info("============= Computing Observation Plan =============");
		
		//////////////////////////// STEP 1 //////////////////////////////
		
		// Creating the array that will contain all possible observations.
		List<Object[]> allObservationsArray = new ArrayList<>();
		
		// Defining our observations : for each timeline of each site, we compute 
		// different observation intervals of 10s, by using a sliding window of
		// 5.001s. For example, for a timeline ]7h10:00:000, 7h10:17:000[, we define
		// 2 observation intervals : 
		// [7h10:00:001 ; 7h10:10:001], [7h10:05:002 ; 7h10:15:002]
		// We took a window step of 5.001s and not 5s to avoid issues with open intervals.
		
		// Defining the step window
		double stepWindow = 5.001; // in seconds.
		
		// Iterating over the sites
		for (final Entry<Site, Timeline> entry : this.accessPlan.entrySet()) {
			
			// Getting the target Site
			final Site target = entry.getKey();
			
			// Getting its access Timeline
			final Timeline timeline = entry.getValue();
			
			// Create the observation law for the current target
			final AttitudeLaw observationLaw = createObservationLaw(target);
			
			// Iterating over the timelines of the site
			for (final Phenomenon accessWindow : timeline.getPhenomenaList()) {
				
				int timelineCount = 1; //Used to name the obsLeg
				
				// Getting the interval corresponding to the timeline
				final AbsoluteDateInterval accessInterval = accessWindow.getTimespan();
				
				// Creating a list containing the middle of the future observations
				final List<AbsoluteDate> middleDateList = accessInterval.getDateList(stepWindow);
				
				// Iterating over the middle dates of the future observations.
				// We don't consider the last middle date, because we may not have
				// enough time after it to create an observation.
				for (int i = 0; i < middleDateList.size() - 1; i++) {
					
					// Creating the observation interval : 10s around the middle date
		            final AbsoluteDate middleDate = middleDateList.get(i);
		            final AbsoluteDate obsStart = middleDate.shiftedBy(-ConstantsBE.INTEGRATION_TIME / 2);
					final AbsoluteDate obsEnd = middleDate.shiftedBy(ConstantsBE.INTEGRATION_TIME / 2);
					final AbsoluteDateInterval obsInterval = new AbsoluteDateInterval(obsStart, obsEnd);
					
					// Creating the corresponding AttitudeLawLeg and naming it
					final String legName = "OBS_" + timelineCount + "_" + i + "_" + target.getName();
					final AttitudeLawLeg obsLeg = new AttitudeLawLeg(observationLaw, obsInterval, legName);
					
					// Computing the score of the observation
					final double scoreObs = MathLib.cos(getEffectiveIncidence(target, obsLeg)) * target.getScore();
					
					// Adding the observation to the array
					allObservationsArray.add(new Object[]{target, obsLeg, scoreObs});
				}
				timelineCount++;
			}
			
		}
		// Sorting the observations in descending order of score
		allObservationsArray.sort((record1, record2) -> Double.compare((double) record2[2], (double) record1[2]));
		
		
		//////////////////////////// STEP 2 //////////////////////////////
		
		// Creating a list which will contain the already observed sites.
		List<String> observedSitesList = new ArrayList<>();
		
		// Iterating over all the possible observations
		outerLoop:
		for (Object[] obs : allObservationsArray) {
            Site currentTarget = (Site) obs[0];
      
            // First condition : the site should not have been already observed
            if (observedSitesList.contains(currentTarget.getName())) {
            	// If it has already been observed, we directly go to
            	// the next observation to insert.
            	continue;
            }
            
            // Defining the start and the end of the observation that we want
            // to insert
            AttitudeLawLeg currentObsLeg = (AttitudeLawLeg) obs[1];
            AbsoluteDateInterval currentInterval = currentObsLeg.getTimeInterval();
            AbsoluteDate currentStartInterval = currentInterval.getLowerData();
            AbsoluteDate currentEndInterval = currentInterval.getUpperData();
            
            // Defining the start and end attitudes of the observation we want to insert.
            Attitude currentStartIntervalAttitude = currentObsLeg.getAttitudeLaw().getAttitude(this.createDefaultPropagator(), 
            		currentStartInterval,this.getEme2000());
            Attitude currentEndIntervalAttitude = currentObsLeg.getAttitudeLaw().getAttitude(this.createDefaultPropagator(), 
            		currentEndInterval, this.getEme2000());
            
            logger.info("---------------------------------------------");
            logger.info("Trying to insert " + currentTarget.getName() + " : " + currentInterval + " in the observation plan...");
            
            // Iterating over all the observations that are already in the plan,
            // in order to test compatibility with the one we want to insert.
            for (AttitudeLawLeg otherObsLeg : observationPlan.values()) {
            	// Remark : In practice, we only need to test compatibility with 
            	// the previous one in the plan, and the following one. Here, we
            	// test the compatibility with all the observations that are in 
            	// the plan, so it's not optimized. But the computation time
            	// is correct.
            	
            	// Defining the start and the end of the observation that is
                // already in the plan
            	AbsoluteDateInterval otherInterval = otherObsLeg.getTimeInterval();
            	AbsoluteDate otherStartInterval = otherInterval.getLowerData();
                AbsoluteDate otherEndInterval = otherInterval.getUpperData();
                
                logger.info("-");
                logger.info("Testing compatibility with " + otherObsLeg.getNature() + " : " + otherInterval);
                
            	// Second condition : it should be the only observation during this interval.
                // For this, we compute the intersection between the observation we want to
                // insert and the one that is already in the plan.
            	if (otherInterval.getIntersectionWith(currentInterval)!=null) {
            		logger.info("Non-empty intersection detected : insertion cancelled");
            		// If the intersection is not empty, we directly go to the next
            		// observation to insert
            		continue outerLoop;
            	}
            	logger.info("Intersection non-empty : OK");
            	
            	// Third condition : the slew duration must be upper than the time between observations.
            	
            	// Testing compatibility with previous observation :
            	// For this, we first compute the time between the end of the observation we want to insert,
            	// and the start of the one that is already in the plan. It must be upper than the slew
            	// duration :
            	
            	// Computing the slew duration
            	Attitude otherStartIntervalAttitude = otherObsLeg.getAttitudeLaw().getAttitude(this.createDefaultPropagator(), 
            			otherStartInterval, this.getEme2000());
            	double slewDurationRight = this.getSatellite().computeSlewDuration(currentEndIntervalAttitude, otherStartIntervalAttitude);
            	
            	if (Math.abs(otherStartInterval.durationFrom(currentEndInterval)) < slewDurationRight) {
            		logger.info("Too short duration with the next observation : insertion cancelled");
            		// If the duration with the previous observation is too short, we directly go to
            		// the next observation to insert
            		continue outerLoop;
            	}
            	
            	// Testing compatibility with following observation :
            	// For this, we first compute the time between the end of the observation that is already
            	// in the plan, and the start of the one we want to insert. It must be upper than the slew
            	// duration :
            	
            	// Computing the slew duration
            	Attitude otherEndIntervalAttitude = otherObsLeg.getAttitudeLaw().getAttitude(this.createDefaultPropagator(), otherEndInterval,
                		this.getEme2000());
            	double slewDurationLeft = this.getSatellite().computeSlewDuration(otherEndIntervalAttitude, currentStartIntervalAttitude);
            	
            	if (Math.abs(currentStartInterval.durationFrom(otherEndInterval)) < slewDurationLeft) {
            		logger.info("Too short duration with the previous observation : insertion cancelled");
            		// If the duration with the following observation is too short, we directly go to
            		// the next observation to insert
            		continue outerLoop;
            	}
            	logger.info("Enough time between the 2 observations : OK");
            	
            }
            
            //Finally adding the observation to the plan if all conditions are met
            this.observationPlan.put(currentTarget, currentObsLeg);
            
            // Updating the list of observed sites
            observedSitesList.add(currentTarget.getName());
            
            logger.info("-");
            logger.info("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Successful insertion of " + currentTarget.getName()
            			+ ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
            logger.info("-");
		}
		
		return this.observationPlan;
	}

	/**
	 * Computes the cinematic plan.
	 * 
	 * @return a {@link StrictAttitudeLegsSequence} that gives all the cinematic
	 *         plan of the {@link Satellite}. It is a chronological sequence of all
	 *         the {@link AttitudeLawLeg} that are necessary to define the
	 *         {@link Attitude} of the {@link Satellite} during all the mission
	 *         horizon. Those legs can have 3 natures : pointing a target site,
	 *         pointing nadir and performing a slew between one of the two previous
	 *         kind of legs.
	 * @throws PatriusException
	 */
	
	public StrictAttitudeLegsSequence<AttitudeLeg> computeCinematicPlan() throws PatriusException {
		/**
		 * We faced an issue when calculating the cinematic plan. We had some problems with the slews from
		 * the nadirs to the sites. First, we computed the observationLaw with the constructor using the
		 * Geodetic point. But we noticed that the slews from the nadir were very long (higher than the
		 * maxSlewDuration). Moreover, when visualizing in VTS, the satellite pointed on other parts of 
		 * the globe. So we concluded that there was an issue with our method createObservationLaw. We
		 * tried to modify it by using 3D vectors and it solved the problems. 
		 * But last week, we asked a teacher about which 3D vectors to use, and he said that we couldn't
		 * use the getZenith() vector.
		 * 
		 * To summarize, it works, but we have a doubt on our createObservationLaw method, because we
		 * still use the getZenith() vector.
		 */

		logger.info("============= Computing Cinematic Plan =============");
		
		// Sorting the observation plan by date of observation.
		// Comparison based on the lower bounds of the time intervals
		Map<Site, AttitudeLawLeg> sortedObservationPlan = this.observationPlan.entrySet()
			    .stream().sorted((entry1, entry2) -> {
			        AbsoluteDateInterval interval1 = entry1.getValue().getTimeInterval();
			        AbsoluteDateInterval interval2 = entry2.getValue().getTimeInterval();
			        return interval1.getLowerData().compareTo(interval2.getLowerData());})
			    	.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
			        (e1, e2) -> e1, LinkedHashMap::new));
		
		// Getting our nadir law
		final AttitudeLaw nadirLaw = this.getSatellite().getDefaultAttitudeLaw();
		
		// Getting the propagator
		final KeplerianPropagator propagator = this.createDefaultPropagator();

		/////////// FIRST STEP : Initial Nadir Law --> Slew --> First Observation ////////////
		
		// Getting the start and end date of the mission
		final AbsoluteDate start = this.getStartDate();
		final AbsoluteDate end = this.getEndDate();
		
		// Getting the first observation and its AttitudeLeg
		Site firstSite = sortedObservationPlan.keySet().iterator().next();
		AttitudeLeg firstObsLeg = sortedObservationPlan.get(firstSite);
		
		// Getting the start of the first observation, and its attitude
		final AbsoluteDate firstObsStart = firstObsLeg.getDate();
		final Attitude startFirstObsAttitude = firstObsLeg.getAttitude(propagator, firstObsStart, getEme2000());
		
		// Defining the end date of the initial Nadir Law, and its attitude
		final AbsoluteDate endInitialNadirLaw = firstObsStart.shiftedBy(-getSatellite().getMaxSlewDuration());
		final Attitude endInitialNadirAttitude = nadirLaw.getAttitude(propagator, endInitialNadirLaw, getEme2000());
		
		// Creating the Attitude Law Legs
		final AttitudeLawLeg initialNadir = new AttitudeLawLeg(nadirLaw, start, endInitialNadirLaw, "Nadir_Law_1");
		final ConstantSpinSlew slewFromInitialNadir = new ConstantSpinSlew(endInitialNadirAttitude, startFirstObsAttitude, 
																			"Slew_Nadir_to_" + firstSite.getName());
		
		// Adding them to the cinematic plan
		this.cinematicPlan.add(initialNadir);
		this.cinematicPlan.add(slewFromInitialNadir);
		this.cinematicPlan.add(firstObsLeg);
		
		/////////// SECOND STEP : Slew from 1st observation -->  2nd Observation --> Slew to Nadir --> ... --> Last Observation ////////////
		
		int sizeObservationPlan = sortedObservationPlan.size();
		int currentIndex = 0; //Used to detect the last Site
		
		// Creating a variable PreviousSite because we need to store it to compute the slew to the next one.
		Site previousSite = firstSite;
		
		// Iterating over all the observations
		for (Map.Entry<Site, AttitudeLawLeg> entry : sortedObservationPlan.entrySet()) {
			
			// We don't process the first site because we already did it before the loop (step 1)
			if (currentIndex == 0) {
				currentIndex++;
				continue;
			}
			
			// Getting the site associated to the next observation
			Site site = entry.getKey();
			
			// Getting the observation legs associated to the previous and the next site
			AttitudeLeg obsLeg = sortedObservationPlan.get(site);
			AttitudeLeg previousObsLeg = sortedObservationPlan.get(previousSite);
			
			// Getting the end of the previous observation, and its attitude
			AbsoluteDate endPreviousObsLeg = previousObsLeg.getEnd();
			Attitude endPreviousObsAttitude = previousObsLeg.getAttitude(propagator, endPreviousObsLeg, getEme2000());
			
			// Getting the start of the next observation, and its attitude
			AbsoluteDate startObsLeg = obsLeg.getDate();
			Attitude startObsAttitude = obsLeg.getAttitude(propagator, startObsLeg, getEme2000());
			
			// If there is enough time between the two observations, we insert a Nadir
			if (startObsLeg.durationFrom(endPreviousObsLeg) > 2 * this.getSatellite().getMaxSlewDuration()) {
				
				// Defining the start and the end date of the Nadir Law, and its attitude
				final AbsoluteDate startNadirLaw = endPreviousObsLeg.shiftedBy(getSatellite().getMaxSlewDuration());
				final AbsoluteDate endNadirLaw = startObsLeg.shiftedBy(-getSatellite().getMaxSlewDuration());
				final Attitude startNadirAttitude = nadirLaw.getAttitude(propagator, startNadirLaw, getEme2000());
				final Attitude endNadirAttitude = nadirLaw.getAttitude(propagator, endNadirLaw, getEme2000());
				
				// Creating the Attitude Law Legs
				final AttitudeLawLeg nadir = new AttitudeLawLeg(nadirLaw, startNadirLaw, endNadirLaw, "Nadir_Law");
				final ConstantSpinSlew slewToNadir = new ConstantSpinSlew(endPreviousObsAttitude, startNadirAttitude, 
														"Slew_from_" + previousSite.getName() + "_to_Nadir");
				final ConstantSpinSlew slewFromNadir = new ConstantSpinSlew(endNadirAttitude, startObsAttitude, 
															"Slew_from_Nadir_to_" + site.getName());
				// Adding them to the cinematic plan
				this.cinematicPlan.add(slewToNadir);
				this.cinematicPlan.add(nadir);
				this.cinematicPlan.add(slewFromNadir);
				this.cinematicPlan.add(obsLeg);	
			}
			
			// Else, we only inert a slew
			else {
				
				// Creating the Attitude Law Leg
				final ConstantSpinSlew slew = new ConstantSpinSlew(endPreviousObsAttitude, startObsAttitude, 
										"Slew_from_" + previousSite.getName() + "_to_" + site.getName() );
				
				// Adding it to the cinematic plan with the next observation
				this.cinematicPlan.add(slew);
				this.cinematicPlan.add(obsLeg);
			}
			
			// If this is the last Site of the observation plan, we create the final Nadir
			if (currentIndex==sizeObservationPlan-1) {
				// Getting the end of the last observation, and its attitude
				AbsoluteDate endObsLeg = obsLeg.getEnd();
				Attitude endObsAttitude = obsLeg.getAttitude(propagator, endObsLeg, getEme2000());
				
				// Creating the start of the final Nadir law, and its attitude
				AbsoluteDate startFinalNadirLaw = endObsLeg.shiftedBy(getSatellite().getMaxSlewDuration());
				Attitude startFinalNadirAttitude = nadirLaw.getAttitude(propagator, startFinalNadirLaw, getEme2000());
				
				// Creating the Attitude Law Legs
				ConstantSpinSlew slewToFinalNadir = new ConstantSpinSlew(endObsAttitude, startFinalNadirAttitude, "Slew_to_final_Nadir");
				AttitudeLawLeg finalNadir = new AttitudeLawLeg(nadirLaw, startFinalNadirLaw, end, "Final_Nadir_Law");
				
				// Adding them to the cinematic plan
				this.cinematicPlan.add(slewToFinalNadir);
				this.cinematicPlan.add(finalNadir);
			}
			
			// Updating the previous site, and the current index
			previousSite = site;
			currentIndex++;
		}
		
		return this.cinematicPlan;
	}

	
	// Here is the first method that we used, but we faced problems with VTS
	// (the satellite pointed anywhere in the globe)
	
	//private AttitudeLaw createObservationLaw(Site target) throws PatriusException {
		/**
		 * To perform an observation, the satellite needs to point the target for a
		 * fixed duration.
		 * 
		 * Here, you will use the {@link TargetGroundPointing}. This law provides a the
		 * Attitude of a Satellite that only points one target at the surface of a
		 * BodyShape. The earth object from the SimpleMission is a BodyShape and we
		 * remind you that the Site object has an attribute which is a GeodeticPoint.
		 * Use those informations to your advantage to build a TargetGroundPointing.
		 * 
		 * Note : to avoid unusual behavior of the TargetGroundPointing law, we advise
		 * you use the following constructor : TargetGroundPointing(BodyShape, Vector3D,
		 * Vector3D, Vector3D) specifying the line of sight axis and the normal axis.
		 */
		/*
		 * Complete the code below to create your observation law and return it
		 */
		/*AttitudeLaw observationLaw = new TargetGroundPointing(
			    this.getEarth(),
			    target.getPoint());
		
		return observationLaw;
	}*/
	
	// Here is the method we use. It works, but the we have a doubt on the vectors
	// that we use.
	
	/**
	 * [COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * Create an observation leg, that is to say an {@link AttitudeLaw} that give
	 * the {@link Attitude} (pointing direction) of the {@link Satellite} in order
	 * to perform the observation of the input target {@link Site}.
	 * 
	 * An {@link AttitudeLaw} is an {@link AttitudeProvider} providing the method
	 * {@link AttitudeProvider#getAttitude()} which can be used to compute the
	 * {@link Attitude} of the {@link Satellite} at any given {@link AbsoluteDate}
	 * (instant) during the mission horizon.
	 * 
	 * An {@link AttitudeLaw} is valid at any time in theory.
	 * 
	 * @param target Input target {@link Site}
	 * @return An {@link AttitudeLawLeg} adapted to the observation.
	 * @throws PatriusException 
	 */
	private AttitudeLaw createObservationLaw(Site target) throws PatriusException {
	     
        AttitudeLaw observationLaw = new TargetGroundPointing(
                this.getEarth(),
                target.getPoint().getZenith(),
                this.getSatellite().getSensorAxis(),
                this.getSatellite().getFrameXAxis()
            );
        
        return observationLaw;
	}
	
	/**
	 * [DO NOT MODIFY THIS METHOD]
	 * 
	 * Computes and returns the angle of incidence (satellite->nadir ;
	 * satellite->target) at the middle date of the input observation.
	 * (same method as in SimpleMission)
	 * 
	 * @param site              Input {@link Site} associated with the
	 *                          {@link AttitudeLawLeg}.
	 * @param observationLawLeg Observation law leg to evaluate. It should be a
	 *                          {@link TargetGroundPointing} targeting a given
	 *                          {@link Site}, and we are going to measure the angle
	 *                          of incidence of the {@link Site}with respect to
	 *                          nadir direction at the middle date of the
	 *                          observation {@link AttitudeLawLeg}.
	 * @return The angle of incidence at the middle date of the input law, as a
	 *         double.
	 * @throws PatriusException If an error occurs during the propagation.
	 */
	private double getEffectiveIncidence(Site site, AttitudeLawLeg observationLawLeg) throws PatriusException {
		// Getting the middleDate, IE the date of the middle of the observation that we
		// are going to use to check the incidence angle.
		final AbsoluteDate middleDate = observationLawLeg.getTimeInterval().getMiddleDate();

		// Creating a new propagator to compute the satellite's pv coordinates
		final KeplerianPropagator propagator = createDefaultPropagator();

		// Calculating the satellite PVCoordinates at middleDate
		final PVCoordinates satPv = propagator.getPVCoordinates(middleDate, this.getEme2000());

		// Calculating the Site PVCoordinates at middleDate
		final TopocentricFrame siteFrame = new TopocentricFrame(this.getEarth(), site.getPoint(), site.getName());
		final PVCoordinates sitePv = siteFrame.getPVCoordinates(middleDate, this.getEme2000());

		// Calculating the normalized site-sat vector at middleDate
		final Vector3D siteSatVectorEme2000 = satPv.getPosition().subtract(sitePv.getPosition()).normalize();

		// Calculating the vector normal to the surface at the Site at middleDate
		final Vector3D siteNormalVectorEarthFrame = siteFrame.getZenith();
		final Transform earth2Eme2000 = siteFrame.getParentShape().getBodyFrame().getTransformTo(this.getEme2000(), middleDate);
		final Vector3D siteNormalVectorEme2000 = earth2Eme2000.transformPosition(siteNormalVectorEarthFrame);

		// Finally, we can compute the incidence angle = angle between
		// siteNormalVectorEme2000 and siteSatVectorEme2000
		final double incidenceAngle = Vector3D.angle(siteNormalVectorEme2000, siteSatVectorEme2000);

		//logger.info("Site : " + site.getName() + " " + site.getPoint().toString());
		//logger.info("Incidence angle rad : " + incidenceAngle);
		//logger.info("Incidence angle deg : " + MathLib.toDegrees(incidenceAngle));

		return incidenceAngle;

	}

	/**
	 * @return the accessPlan
	 */
	public Map<Site, Timeline> getAccessPlan() {
		return this.accessPlan;
	}

	/**
	 * @return the observationPlan
	 */
	public Map<Site, AttitudeLawLeg> getObservationPlan() {
		return this.observationPlan;
	}

	/**
	 * @return the cinematicPlan
	 */
	public StrictAttitudeLegsSequence<AttitudeLeg> getCinematicPlan() {
		return this.cinematicPlan;
	}

	@Override
	public String toString() {
		return "CompleteMission [name=" + this.getName() + ", startDate=" + this.getStartDate() + ", endDate="
				+ this.getEndDate() + ", satellite=" + this.getSatellite() + "]";
	}
}

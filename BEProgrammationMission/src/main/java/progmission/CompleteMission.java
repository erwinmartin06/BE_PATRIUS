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
	//private final Map<Site, AttitudeLawLeg> observationPlan;
	private Map<Site, AttitudeLawLeg> observationPlan;

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
	 * [COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
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

		/**
		 * Step 2 :
		 * 
		 * Combine the timelines with logical gates and retrieve only the access
		 * conditions through a refined Timeline object.
		 * 
		 * For that, you can use the classes in the events.postprocessing module : for
		 * example, the AndCriterion or the NotCriterion.
		 * 
		 * Finally, you can filter only the Phenomenon matching a certain condition
		 * using the ElementTypeFilter
		 */
		

		Timeline siteAccessTimeline = createSiteGlobalTimeline(targetSite);

		// Define and use your own criteria, here is an example (use the right strings
		// defined when naming the phenomenon in the GenericCodingEventDetector)
		final AndCriterion andCriterion = new AndCriterion("Satellite visibility", "Illumination",
				"Satellite visibility and illumination", "Comment about this phenomenon");
		// Applying our criterion adds all the new phenonmena inside the global timeline
		andCriterion.applyTo(siteAccessTimeline);
		
		
		final NotCriterion notCriterion = new NotCriterion("Dazzling",
				"No dazzling", "Comment") ;
				
		notCriterion.applyTo(siteAccessTimeline);
		
		final AndCriterion andCriterion2 = new AndCriterion("Satellite visibility and illumination", "No dazzling",
				"Satellite visibility and illumination and no dazzling", "Comment about this phenomenon");
		// Applying our criterion adds all the new phenonmena inside the global timeline
		andCriterion2.applyTo(siteAccessTimeline);

		// Then create an ElementTypeFilter that will filter all phenomenon not
		// respecting the input condition you gave it
		final ElementTypeFilter obsConditionFilter = new ElementTypeFilter(
				"Satellite visibility and illumination and no dazzling", false);
		
		// Finally, we filter the global timeline to keep only X1 AND X2 phenomena
		obsConditionFilter.applyTo(siteAccessTimeline);

		/*
		 * Now make sure your globalTimeline represents the access Timeline for the
		 * input target Site and it's done ! You can print the Timeline using the
		 * utility module of the BE as below
		 */

		// Log the final access timeline associated to the current target
		logger.info("\n" + targetSite.getName());
		ProjectUtils.printTimeline(siteAccessTimeline);

		return siteAccessTimeline;
	}

	private Timeline createSiteGlobalTimeline(Site targetSite) throws PatriusException {

		final EventDetector constraintVisibilityDetector = createConstraintVisibilityDetector(targetSite);

		this.getSatellite().getPropagator().addEventDetector(constraintVisibilityDetector);

		final GenericCodingEventDetector codingEventVisibilityDetector = new GenericCodingEventDetector(constraintVisibilityDetector,
				"Start of satellite visibility", "End of satellite visibility", true, "Satellite visibility");
		final CodedEventsLogger eventVisibilityLogger = new CodedEventsLogger();
		final EventDetector eventVisibilityDetector = eventVisibilityLogger.monitorDetector(codingEventVisibilityDetector);
		// Then you add your logger to the propagator, it will monitor the event coded
		// by the codingEventDetector
		this.getSatellite().getPropagator().addEventDetector(eventVisibilityDetector);

//////////////////////////////////////////////////

		final EventDetector constraintIlluminationDetector = createConstraintIlluminationDetector(targetSite);

		this.getSatellite().getPropagator().addEventDetector(constraintIlluminationDetector);

		final GenericCodingEventDetector codingEventIlluminationDetector = new GenericCodingEventDetector(constraintIlluminationDetector,
				"Start of illumination", "End of illumination", true, "Illumination");
		final CodedEventsLogger eventIlluminationLogger = new CodedEventsLogger();
		final EventDetector eventIlluminationDetector = eventIlluminationLogger.monitorDetector(codingEventIlluminationDetector);
		
		this.getSatellite().getPropagator().addEventDetector(eventIlluminationDetector);

//////////////////////////////////////////////////////		

		final EventDetector constraintDazzlingDetector = createConstraintDazzlingDetector(targetSite);

		this.getSatellite().getPropagator().addEventDetector(constraintDazzlingDetector);

		final GenericCodingEventDetector codingEventDazzlingDetector = new GenericCodingEventDetector(constraintDazzlingDetector,
				"Start of dazzling", "End of dazzling", true, "Dazzling");
		final CodedEventsLogger eventDazzlingLogger = new CodedEventsLogger();
		final EventDetector eventDazzlingDetector = eventDazzlingLogger.monitorDetector(codingEventDazzlingDetector);
		
		this.getSatellite().getPropagator().addEventDetector(eventDazzlingDetector);

/////////////////////////////////////////////////////////
		// Finally propagating the orbit
		this.getSatellite().getPropagator().propagate(this.getStartDate(), this.getEndDate());
		
/////////////////////////////////////////////////////////
		final Timeline timeline1 = new Timeline(eventVisibilityLogger,
		new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()), null);

		final Timeline timeline2 = new Timeline(eventIlluminationLogger,
				new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()), null);

		final Timeline timeline3 = new Timeline(eventDazzlingLogger,
				new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()), null); 
		/////////////////////

		final Timeline siteAccessTimeline = new Timeline(
				new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()));
		// Adding the phenomena of all the considered timelines
		for (final Phenomenon phenom : timeline1.getPhenomenaList()) {
			siteAccessTimeline.addPhenomenon(phenom);
		}
		for (final Phenomenon phenom : timeline2.getPhenomenaList()) {
			siteAccessTimeline.addPhenomenon(phenom);
		}
		for (final Phenomenon phenom : timeline3.getPhenomenaList()) {
			siteAccessTimeline.addPhenomenon(phenom); 
		} 

		return siteAccessTimeline;

	}
	/**
	 * [COPY-PASTE AND COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * This method should compute a {@link Timeline} object which encapsulates all
	 * the {@link Phenomenon} corresponding to a orbital phenomenon X relative to
	 * the input target {@link Site}. For example, X can be the {@link Site}
	 * visibility phenomenon.
	 * 
	 * You can copy-paste this method and adapt it for every X {@link Phenomenon}
	 * and {@link Timeline} you need to implement. The global process described here
	 * stays the same.
	 * 
	 * @param targetSite Input target {@link Site}
	 * @return The {@link Timeline} containing all the {@link Phenomenon} relative
	 *         to the X phenomenon to monitor.
	 * @throws PatriusException If a {@link PatriusException} occurs when creating
	 *                          the {@link Timeline}.
	 */
	

	/**
	 * [COPY-PASTE AND COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * Create an adapted instance of {@link EventDetector} matching the input need
	 * for monitoring the events defined by the X constraint. (X can be a lot of
	 * things).
	 * 
	 * You can copy-paste this method to adapt it to the {@link EventDetector} X
	 * that you want to create.
	 * 
	 * Note: this can have different inputs that we don't define here
	 * 
	 * @return An {@link EventDetector} answering the constraint (for example a
	 *         {@link SensorVisibilityDetector} for a visibility constraint).
	 */
	private EventDetector createConstraintVisibilityDetector(Site targetSite) {
		
		SensorModel sensorModel = new SensorModel(this.getSatellite().getAssembly(), "sensor");
		
		sensorModel.addMaskingCelestialBody(this.getEarth());
		PVCoordinatesProvider target = new TopocentricFrame(this.getEarth(), targetSite.getPoint(),targetSite.getName());
		LocalRadiusProvider radius = new ConstantRadiusProvider(0);
		sensorModel.setMainTarget(target, radius);
		
		EventDetector visibilityDetector = new SensorVisibilityDetector(sensorModel, MAXCHECK_EVENTS, TRESHOLD_EVENTS, EventDetector.Action.CONTINUE, 
				EventDetector.Action.CONTINUE);

		return visibilityDetector;
	}
	
	/**
	 * 
	 * Create an adapted instance of {@link EventDetector} matching the input need
	 * for monitoring the events defined by the illumination constraint.
	 * 
	 * 
	 * Note: this can have different inputs that we don't define here
	 * 
	 * @return An {@link EventDetector} answering the constraint (for example a
	 *         {@link SensorVisibilityDetector} for a visibility constraint).
	 */
	private EventDetector createConstraintIlluminationDetector(Site targetSite) {
		
		
		PVCoordinatesProvider target = new TopocentricFrame(this.getEarth(), targetSite.getPoint(),targetSite.getName());
		EventDetector illuminationDetector = new ThreeBodiesAngleDetector(this.getEarth(), target ,
				this.getSun(), MathLib.toRadians(180-ConstantsBE.MAX_SUN_INCIDENCE_ANGLE), MAXCHECK_EVENTS, 
				TRESHOLD_EVENTS, EventDetector.Action.CONTINUE );
		
		
		return illuminationDetector;
	}
	
	/**
	 * 
	 * Create an adapted instance of {@link EventDetector} matching the input need
	 * for monitoring the events defined by the illumination constraint.
	 * 
	 * 
	 * Note: this can have different inputs that we don't define here
	 * 
	 * @return An {@link EventDetector} answering the constraint (for example a
	 *         {@link SensorVisibilityDetector} for a visibility constraint).
	 */
	private EventDetector createConstraintDazzlingDetector(Site targetSite) {
		
		
		PVCoordinatesProvider target = new TopocentricFrame(this.getEarth(), targetSite.getPoint(),targetSite.getName());
		EventDetector DazzlingDetector = new ThreeBodiesAngleDetector(this.getSatellite().getPropagator().getPvProvider(), target, 
				this.getSun(), MathLib.toRadians(ConstantsBE.MAX_SUN_PHASE_ANGLE), MAXCHECK_EVENTS, TRESHOLD_EVENTS,
				EventDetector.Action.CONTINUE );
		
		
		return DazzlingDetector;
	}

	/**
	 * [COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * Compute the access plan.
	 * 
	 * Reminder : the access plan corresponds to the object gathering all the
	 * opportunities of access for all the sites of interest during the mission
	 * horizon. One opportunity of access is defined by an access window (an
	 * interval of time during which the satellite can observe the target and during
	 * which all the observation conditions are achieved : visibility, incidence
	 * angle, illumination of the scene,etc.). Here, we suggest you use the Patrius
	 * class {@link Timeline} to encapsulate all the access windows of each site of
	 * interest. One access window will then be described by the {@link Phenomenon}
	 * object, itself defined by two {@link CodedEvent} objects giving the start and
	 * end of the access window. Please find more tips and help in the submethods of
	 * this method.
	 * 
	 * @return the sites access plan with one {@link Timeline} per {@link Site}
	 * @throws PatriusException If a {@link PatriusException} occurs during the
	 *                          computations
	 */
	public Map<Site, Timeline> computeAccessPlan() throws PatriusException {
		/**
		 * Here you need to compute one access Timeline per target Site. You can start
		 * with only one site and then try to compute all of them.
		 * 
		 * Note : when computing all the sites, try to make sure you don't decrease the
		 * performance of the code too much. You might have some modifications to do in
		 * order to ensure a reasonable time of execution.
		 */
		logger.info("============= Computing Access Plan =============");
		
		for (Site targetSite : this.getSiteList()) {
			logger.info(" Site : " + targetSite.getName());
	
			// Checking if the Site access Timeline has already been serialized or not
			final String filename = generateSerializationName(targetSite, hashConstantBE);
			File file = new File(filename);
			boolean loaded = false;
	
			// If the file exist for the current Site, we try to load its content
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
	 * [COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
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
		 * Here are the big constraints and informations you need to build an
		 * observation plan.
		 * 
		 * Reminder : we can perform only one observation per site of interest during
		 * the mission horizon.
		 * 
		 * Objective : Now we have our access plan, listing for each Site all the access
		 * windows. There might be up to one access window per orbit pass above each
		 * site, so we have to decide for each Site which access window will be used to
		 * achieve the observation of the Site. Then, during one access window, we have
		 * to decide when precisely we perform the observation, which lasts a constant
		 * duration which is much smaller than the access window itself (see
		 * ConstantsBE.INTEGRATION_TIME for the duration of one observation). Finally,
		 * we must respect the cinematic constraint : using the
		 * Satellite#computeSlewDuration() method, we need to ensure that the
		 * theoritical duration of the slew between two consecutive observations is
		 * always smaller than the actual duration between those consecutive
		 * observations. Same goes for the slew between a Nadir pointing law and another
		 * poiting law. Of course, we cannot point two targets at once, so we cannot
		 * perform two observations during the same AbsoluteDateInterval !
		 * 
		 * Tip 1 : Here you can use the greedy algorithm presented in class, or any
		 * method you want. You just have to ensure that all constraints are respected.
		 * This is a non linear, complex optimization problem (scheduling problem), so
		 * there is no universal answer. Even if you don't manage to build an optimal
		 * plan, try to code a suboptimal algorithm anyway, we will value any idea you
		 * have. For example, try with a plan where you have only one observation per
		 * satellite pass over France. With that kind of plan, you make sure all
		 * cinematic constraint are respected (no slew to fast for the satellite
		 * agility) and you have a basic plan to use to build your cinematic plan and
		 * validate with VTS visualization.
		 * 
		 * Tip 2 : We provide the observation plan format : a Map of AttitudeLawLeg. In
		 * doing so, we give you the structure that you must obtain in order to go
		 * further. If you check the Javadoc of the AttitudeLawLeg class, you see that
		 * you have two inputs. First, you must provide a specific interval of time that
		 * you have to chose inside one of the access windows of your access plan. Then,
		 * we give you which law to use for observation legs : TargetGroundPointing.
		 * 
		 */
		logger.info("============= Computing Observation Plan =============");
		/*
		 * We provide a basic and incomplete code that you can use to compute the
		 * observation plan.
		 * 
		 * Here the only thing we do is printing all the access opportunities using the
		 * Timeline objects. We get a list of AbsoluteDateInterval from the Timelines,
		 * which is the basis of the creation of AttitudeLawLeg objects since you need
		 * an AbsoluteDateInterval or two AbsoluteDates to do it.
		 */
		
		double stepWindow = 5.001; // in seconds. We take .001 to avoid issues with open intervals
		
		// The idea is first to create an array which will contain all observations.
		// For each city, we have several timelines. For each timeline, we will compute different observation windows of 10s, by using a sliding
		// window of 5s. 
		// Then, we will have many possible observation intervals. We store them in the array allObservationArray, and we will sort them by
		// decreasing score.
		
		List<Object[]> allObservationsArray = new ArrayList<>(); // array which will contain all possible observations : target, obs, score
		
		for (final Entry<Site, Timeline> entry : this.accessPlan.entrySet()) {
			// Scrolling through the entries of the accessPlan
			// Getting the target Site
			final Site target = entry.getKey();
			//logger.info("Current target site : " + target.getName());
			// Getting its access Timeline
			final Timeline timeline = entry.getValue();
			// Getting the access intervals
			final AbsoluteDateIntervalsList accessIntervals = new AbsoluteDateIntervalsList();
			// Create the observation law for the current target
			final AttitudeLaw observationLaw = createObservationLaw(target);
			
			for (final Phenomenon accessWindow : timeline.getPhenomenaList()) {
				int timelineCount = 1;
				// The Phenomena are sorted chronologically so the accessIntervals List is too
				final AbsoluteDateInterval accessInterval = accessWindow.getTimespan();
				accessIntervals.add(accessInterval);
				final List<AbsoluteDate> middleDateList = accessInterval.getDateList(stepWindow); //we obtain the middle of intervals
				//logger.info(middleDateList.toString());
				//logger.info(accessInterval.toString());
				
				for (int i = 0; i < middleDateList.size() - 1; i++) { // we don't take the last interval because it can last less than 10s
		            final AbsoluteDate middleDate = middleDateList.get(i);
		            final AbsoluteDate obsStart = middleDate.shiftedBy(-ConstantsBE.INTEGRATION_TIME / 2);
					final AbsoluteDate obsEnd = middleDate.shiftedBy(ConstantsBE.INTEGRATION_TIME / 2);
					final AbsoluteDateInterval obsInterval = new AbsoluteDateInterval(obsStart, obsEnd);
					// Then, we create our AttitudeLawLeg, that we name using the name of the target
					final String legName = "OBS_" + timelineCount + "_" + i + "_" + target.getName();
					final AttitudeLawLeg obsLeg = new AttitudeLawLeg(observationLaw, obsInterval, legName);
					// We compute the score of the observation
					final double scoreObs = MathLib.cos(getEffectiveIncidence(target, obsLeg)) * target.getScore();
					allObservationsArray.add(new Object[]{target, obsLeg, scoreObs});
				}
				timelineCount++;
			}
			
		}
		// We sort the observations in descending order of score.
		allObservationsArray.sort((record1, record2) -> Double.compare((double) record2[2], (double) record1[2]));
		
		// Next step of the code : use allObservationArray to compute the observation plan, by prioritizing the highest scores.
		List<String> observedSitesList = new ArrayList<>(); //List which will contain the already observed sites
		
		outerLoop:
		for (Object[] obs : allObservationsArray) {
            Site currentTarget = (Site) obs[0];
      
            // First condition : the site should not have been already observed
            if (observedSitesList.contains(currentTarget.getName())) {
            	continue;
            }
            
            AttitudeLawLeg currentObsLeg = (AttitudeLawLeg) obs[1];
            AbsoluteDateInterval currentInterval = currentObsLeg.getTimeInterval();
            AbsoluteDate currentStartInterval = currentInterval.getLowerData();
            AbsoluteDate currentEndInterval = currentInterval.getUpperData();
            
            Attitude currentStartIntervalAttitude = currentObsLeg.getAttitudeLaw().getAttitude(this.createDefaultPropagator(), currentStartInterval,
            		this.getEme2000());
            Attitude currentEndIntervalAttitude = currentObsLeg.getAttitudeLaw().getAttitude(this.createDefaultPropagator(), currentEndInterval,
            		this.getEme2000());
            logger.info("---------------------------------------------");
            logger.info("Trying to insert " + currentTarget.getName() + " : " + currentInterval + " in the observation plan...");
            
            for (AttitudeLawLeg otherObsLeg : observationPlan.values()) {
            	AbsoluteDateInterval otherInterval = otherObsLeg.getTimeInterval();
            	AbsoluteDate otherStartInterval = otherInterval.getLowerData();
                AbsoluteDate otherEndInterval = otherInterval.getUpperData();
                logger.info("-");
                logger.info("Testing compatibility with " + otherObsLeg.getNature() + " : " + otherInterval);
                
            	// Second condition : it should be the only observation during this interval
            	if (otherInterval.getIntersectionWith(currentInterval)!=null) {
            		logger.info("Non-empty intersection detected : insertion cancelled");
            		continue outerLoop;
            	}
            	logger.info("Intersection non-empty : OK");
            	// Third condition : the slew duration must be upper than the time between observations
            	
            	Attitude otherStartIntervalAttitude = otherObsLeg.getAttitudeLaw().getAttitude(this.createDefaultPropagator(), otherStartInterval,
                		this.getEme2000());
            	double slewDurationRight = this.getSatellite().computeSlewDuration(currentEndIntervalAttitude, otherStartIntervalAttitude);
            	
            	//logger.info("Right delta_T : " + Math.abs(otherStartInterval.durationFrom(currentEndInterval)));
            	//logger.info("Right slew duration :" + slewDurationRight);
            	if (Math.abs(otherStartInterval.durationFrom(currentEndInterval)) < slewDurationRight) {
            		logger.info("Too short duration with the next observation : insertion cancelled");
            		continue outerLoop;
            	}
        
            	Attitude otherEndIntervalAttitude = otherObsLeg.getAttitudeLaw().getAttitude(this.createDefaultPropagator(), otherEndInterval,
                		this.getEme2000());
            	double slewDurationLeft = this.getSatellite().computeSlewDuration(otherEndIntervalAttitude, currentStartIntervalAttitude);
         
            	//logger.info("Left delta_T : " + Math.abs(currentStartInterval.durationFrom(otherEndInterval)));
            	//logger.info("Left slew duration :" + slewDurationLeft);
            	if (Math.abs(currentStartInterval.durationFrom(otherEndInterval)) < slewDurationLeft) {
            		logger.info("Too short duration with the previous observation : insertion cancelled");
            		continue outerLoop;
            	}
            	logger.info("Enough time between the 2 observations : OK");
            	
            }
            logger.info("-");
            logger.info("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Successful insertion of " + currentTarget.getName() + ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
            logger.info("-");
            observedSitesList.add(currentTarget.getName());
            this.observationPlan.put(currentTarget, currentObsLeg);
            
		}
		
		//
		//logger.info("Size of total array : " + allObservationsArray.size());
		this.observationPlan = this.observationPlan.entrySet()
			    .stream()
			    .sorted((entry1, entry2) -> {
			        // Comparaison basée sur les bornes inférieures des intervalles de temps
			        AbsoluteDateInterval interval1 = entry1.getValue().getTimeInterval();
			        AbsoluteDateInterval interval2 = entry2.getValue().getTimeInterval();
			        return interval1.getLowerData().compareTo(interval2.getLowerData());
			    })
			    .collect(Collectors.toMap(
			        Map.Entry::getKey,
			        Map.Entry::getValue,
			        (e1, e2) -> e1, // Résolution des collisions (non applicable ici)
			        LinkedHashMap::new // Maintient l'ordre des éléments triés
			    ));
        
		return this.observationPlan;
	}

	/**
	 * [COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * Computes the cinematic plan.
	 * 
	 * Here you need to compute the cinematic plan, which is the cinematic chain of
	 * attitude law legs (observation, default law and slews) needed to perform the
	 * mission. Usually, we start and end the mission in default law and during the
	 * horizon, we alternate between default law, observation legs and slew legs.
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
		 * Now we want to assemble a continuous attitude law which is valid during all
		 * the mission horizon. For that, we will use to object
		 * StrictAttitudeLegsSequence<AttitudeLeg> which is a chronological sequence of
		 * AttitudeLeg. In our case, each AttitudeLeg will be an AttitudeLawLeg, either
		 * a leg of site observation, a slew, or the nadir pointing attitude law (see
		 * the Satellite constructor and the BodyCenterGroundPointing class, it's the
		 * Satellite default attitude law). For more help about the Attitude handling,
		 * use the module 11 of the patrius formation.
		 * 
		 * Tip 1 : Please give names to the different AttitudeLawLeg you build so that
		 * you can visualize them with VTS later on. For example "OBS_Paris" when
		 * observing Paris or "SlEW_Paris_Lyon" when adding a slew from Paris
		 * observation AttitudeLawLeg to Lyon observation AttitudeLawLeg.
		 * 
		 * Tip 2 : the sequence you want to obtain should look like this :
		 * [nadir-slew-obs1-slew-obs2-slew-obs3-slew-nadir] for the simple version where
		 * you don't try to fit nadir laws between observations or
		 * [nadir-slew-obs1-slew-nadir-selw-obs2-slew-obs3-slew-nadir] for the more
		 * complexe version with nadir laws if the slew during two observation is long
		 * enough.
		 * 
		 * Tip 3 : You can use the class ConstantSpinSlew(initialAttitude,
		 * finalAttitude, slewName) for the slews. This an AtittudeLeg so you will be
		 * able to add it to the StrictAttitudeLegsSequence as every other leg.
		 */
		logger.info("============= Computing Cinematic Plan =============");
		/*
		 * Example of code using our observation plan, let's say we only have one obs
		 * pointing Paris.
		 * 
		 * Then we are going to create a very basic cinematic plan : nadir law => slew
		 * => obsParis => slew => nadir law
		 * 
		 * To do that, we need to compute the slew duration from the end of nadir law to
		 * the begining of Paris obs and then from the end of Paris obs to the begining
		 * of nadir law. For that, we use the Satellite#computeSlewDurationMethod() as
		 * before. We know we have to the time to perform the slew thanks to the
		 * cinematic checks we already did during the observation plan computation.
		 */
		
		// Getting our nadir law
		final AttitudeLaw nadirLaw = this.getSatellite().getDefaultAttitudeLaw();
		//Getting the propagator
		final KeplerianPropagator propagator = this.createDefaultPropagator();

		// Getting all the dates we need to compute our slews
		final AbsoluteDate start = this.getStartDate();
		final AbsoluteDate end = this.getEndDate();
		
		// Get the first observation
		Site firstSite = observationPlan.keySet().iterator().next();
		AttitudeLeg firstObsLeg = observationPlan.get(firstSite);
		
		final AbsoluteDate firstObsStart = firstObsLeg.getDate();
		final Attitude startFirstObsAttitude = firstObsLeg.getAttitude(propagator, firstObsStart, getEme2000());
		
		logger.info("caca " + this.getSatellite().getMaxSlewDuration());
		final AbsoluteDate endNadirLaw1 = firstObsStart.shiftedBy(-getSatellite().getMaxSlewDuration());
		final Attitude endNadir1Attitude = nadirLaw.getAttitude(propagator, endNadirLaw1, getEme2000());
		final AttitudeLawLeg nadir1 = new AttitudeLawLeg(nadirLaw, start, endNadirLaw1, "Nadir_Law_1");
		final ConstantSpinSlew slew1 = new ConstantSpinSlew(endNadir1Attitude, startFirstObsAttitude, "Slew_Nadir_to_" + firstSite.getName());
			
		
		this.cinematicPlan.add(nadir1);
		this.cinematicPlan.add(slew1);
		this.cinematicPlan.add(firstObsLeg);
		
		int i = 1;
		for (Map.Entry<Site, AttitudeLawLeg> entry : observationPlan.entrySet()) {
			Site site = entry.getKey();
			if (site.getName().equals(firstSite.getName())) {
				continue;
			}
			AttitudeLeg obsLeg = observationPlan.get(site);
			AbsoluteDate startObsLeg = obsLeg.getDate();
			Attitude startObsAttitude = obsLeg.getAttitude(propagator, startObsLeg, getEme2000());
			AbsoluteDate endPreviousObsLeg = firstObsLeg.getEnd();
			Attitude endPreviousObsAttitude = firstObsLeg.getAttitude(propagator, endPreviousObsLeg, getEme2000());
			
			if (startObsLeg.durationFrom(endPreviousObsLeg) > 2 * this.getSatellite().getMaxSlewDuration()) {
				final AbsoluteDate startNadirLaw = endPreviousObsLeg.shiftedBy(getSatellite().getMaxSlewDuration());
				final AbsoluteDate endNadirLaw = startObsLeg.shiftedBy(-getSatellite().getMaxSlewDuration());
				
				final Attitude startNadirAttitude = nadirLaw.getAttitude(propagator, startNadirLaw, getEme2000());
				final Attitude endNadirAttitude = nadirLaw.getAttitude(propagator, endNadirLaw, getEme2000());
				
				final AttitudeLawLeg nadir = new AttitudeLawLeg(nadirLaw, startNadirLaw, endNadirLaw, "Nadir_Law_to_" + site.getName()  );
				
				final ConstantSpinSlew slewToNadir = new ConstantSpinSlew(endPreviousObsAttitude, startNadirAttitude, "Slew_to_Nadir");
				final ConstantSpinSlew slewFromNadir = new ConstantSpinSlew(endNadirAttitude, startObsAttitude, "Slew_from_Nadir_to" + site.getName());
				this.cinematicPlan.add(slewToNadir);
				this.cinematicPlan.add(nadir);
				this.cinematicPlan.add(slewFromNadir);
				this.cinematicPlan.add(obsLeg);
				
			}
			else {
				
				final ConstantSpinSlew slew = new ConstantSpinSlew(endPreviousObsAttitude, startObsAttitude, "Slew_to_" + site.getName());
				this.cinematicPlan.add(slew);
				this.cinematicPlan.add(obsLeg);
			}	
			firstObsLeg = obsLeg;
		}
		

		/**
		 * Now your job is finished, the two following methods will finish the job for
		 * you : checkCinematicPlan() will check that each slew's duration is longer
		 * than the theoritical duration it takes to perform the same slew. Then, if the
		 * cinematic plan is valid, computeFinalScore() will compute the score of your
		 * observation plan. Finaly, generateVTSVisualization will write all the
		 * ephemeris (Position/Velocity + Attitude) and generate a VTS simulation that
		 * you will be able to play to visualize and validate your plans.
		 */
		return this.cinematicPlan;
	}

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
	 * An {@link AttitudeLaw} is valid at anu time in theory.
	 * 
	 * @param target Input target {@link Site}
	 * @return An {@link AttitudeLawLeg} adapted to the observation.
	 * @throws PatriusException 
	 */
	private AttitudeLaw createObservationLaw(Site target) throws PatriusException {
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
		AttitudeLaw observationLaw = new TargetGroundPointing(
			    this.getEarth(),
			    target.getPoint());
		
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

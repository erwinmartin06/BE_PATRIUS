--------------------------------------------------------------------------------
-- vtsClient.lua
--
-- HISTORIQUE
-- VERSION : 3.5 : DM : DM_578 : 14/12/2020 : Visualisation à plusieurs milliers d'objets
-- VERSION : 3.5 : FA : FA_565 : 14/12/2020 : Sélection d'un layer depuis Colorbar
-- VERSION : 3.5 : FA : FA_543 : 14/12/2020 : Problème Celestia avec les accents
-- VERSION : 3.5 : DM : DM_534 : 14/12/2020 : Affichage des liens de visibilité géométrique entre satellites
-- VERSION : 3.5 : DM : DM_320 : 14/12/2020 : Afficher le cône d'ombre du fait de la Terre en 3D
-- VERSION : 3.4 : DM : DM_531 : 10/12/2019 : Affichage de l'orbit path pour les bodies
-- VERSION : 3.4 : DM : DM_527 : 10/12/2019 : Masquage de tous les éléments liés à un satellite
-- VERSION : 3.4 : DM : DM_515 : 10/12/2019 : Affichage des liens de visibilité géométrique satellite-station dans Celestia
-- VERSION : 3.4 : DM : DM_498 : 10/12/2019 : Définition des champs de vue via Azimut et Elévation
-- VERSION : 3.3 : DM : DM_332 : 07/12/2018 : Prise en charge du format OBJ pour les modèles 3D
-- VERSION : 3.2 : DM : DM_426 : 07/12/2017 : Renommage d'entités dans les states
-- VERSION : 3.2 : DM : DM_413 : 07/12/2017 : Covariance de la position
-- VERSION : 3.1 : DM : DM_343 : 06/02/2017 : Caméra Station vers Satellite en 3D
-- VERSION : 3.0 : FA : FA_367 : 05/07/2016 : Camera par défaut si pas de satellite
-- VERSION : 2.7.1 : FA : FA_280 : 18/09/2015 : Incohérence dégré/radian dans le protocole
-- VERSION : 2.7 : DM : DM_258 : 15/07/2015 : Pouvoir masquer les stations
-- VERSION : 2.7 : DM : DM_254 : 15/07/2015 : Meilleure caméra par défaut
-- VERSION : 2.6 : DM : DM_198 : 14/11/2014 : Rotation de la caméra senseur
-- VERSION : 2.6 : DM : DM_187 : 14/11/2014 : Points of interest
-- VERSION : 2.6 : DM : DM_185 : 14/11/2014 : Rosetta : mémorisiation caméra Civa
-- VERSION : 2.6 : DM : DM_184 : 14/11/2014 : Rosetta : caméra Civa
-- VERSION : 2.6 : DM : DM_175 : 14/11/2014 : Longueur des trajectoires dynamiques
-- VERSION : 2.6 : DM : DM_55 : 14/11/2014 : DM - automatisation des tests de non-régression
-- VERSION : 2.6 : DM : DM_53 : 14/11/2014 : DM - Zones d'intérêt sur la 2DWIn
-- VERSION : 2.6 : DM : DM_37 : 14/11/2014 : DM - Simulation senseur pour demonstrateur de rendez-vous.
-- VERSION : 2.5 : FA : FA_148 : 06/03/2014 : Protocole : messages de preloading des textures superflus
-- VERSION : 2.5 : DM : DM_127 : 06/03/2014 : Automatisation des prises de vue
-- VERSION : 2.5 : DM : DM_50 : 06/03/2014 : DM - Caméras manuelles
-- VERSION : 2.4 : DM : DM_142 : 16/12/2013 : Visualiseur de plans dans Cosmographia
-- VERSION : 2.4 : DM : DM_70 : 16/12/2013 : DM - Suite de la consolidation de cosmographia dans VTS
-- VERSION : 2.4 : DM : DM_65 : 16/12/2013 : DM intégration de la Fauchée dans VTS
-- VERSION : 2.3 : FA : FA_124 : 09/07/2013 : Window Text de Celestia doit forcer son affichage
-- VERSION : 2.3 : FA : FA_116 : 09/07/2013 : Problème LUA dans Cosmographia avec nil et NULL
-- VERSION : 2.3 : DM : DM_44 : 09/07/2013 : DM - _ref
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Ajout des caméras polaires )
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Celestia : Possibilité de cacher les menus et les textes )
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Celestia : Sauvegarde de la lumière ambiante )
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Cosmographia : intégration de LUA (DM51) )
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Maintenance corrective (Corrections mineures) )
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Textures dynamiques )
-- VERSION : 2.2 : DM : DM_60 : 19/02/2013 : Ajout des axes body et de la grille
-- VERSION : 2.1 : DM : VTS 2012 : 23/07/2012 : VTS 2.1 ( Lot 1 - Grp E - Films )
-- VERSION : 2.0 : DM : VTS 2012 : 23/07/2012 : VTS 2.0 ( DM 36 - Conservation du parametrage )
-- VERSION : 2.0 : DM : VTS 2012 : 23/07/2012 : VTS 2.0 ( DM 37 - Prototype Z-buffer )
-- VERSION : 2.0 : DM : VTS 2012 : 23/07/2012 : VTS 2.0 ( DM 38 - Temps-reel )
-- VERSION : 2.0 : DM : DM_45 : 23/07/2012 : DM - Reférentiels et Repères
-- VERSION : 2.0 : FA : FA_6 : 23/07/2012 : FA Broker - Celestia - Pas d'affichage d'orbite en pause
-- VERSION : 1.3.0 : DM : CELEST-SB-DOC-3361-CN : 27/02/2012 : VTS 1.3.0
-- VERSION : 1.3.0 : DM : CELEST-SB-DOC-3361-CN : 27/02/2012 : VTS 1.3.0 ( VTS 2011 )
-- VERSION : 1.2.2 : FA : FA_-9 : 14/04/2011 : Le contour du volume de visée des senseurs 3D ne peut être affiché seul
-- VERSION : 1.2.2 : DM : CELEST-SB-DOC-3361-CN : 14/04/2011 : Complément VTS 2010
-- VERSION : 1.2.2 : DM : CELEST-SB-DOC-3361-CN : 14/04/2011 : Complément VTS 2010 ( Lot 2 )
-- VERSION : 1.2.2 : DM : CELEST-SB-DOC-3361-CN : 14/04/2011 : Complément VTS 2010 ( Lot 4A )
-- FIN-HISTORIQUE
-- Copyright © (2020) CNES All rights reserved
--
-- VER : $Id: vtsClient.lua 8612 2020-12-09 20:02:41Z mmo $
--------------------------------------------------------------------------------



--------------------------------------------------------------------------------
-- Utilisation des modules externes
--------------------------------------------------------------------------------

require( "vtsProperties" )


--------------------------------------------------------------------------------
-- Variables locales
--------------------------------------------------------------------------------

local firstTick = true


--------------------------------------------------------------------------------
-- receiveData
--
-- Traitement d'un paquet de donnees
--------------------------------------------------------------------------------

function receiveData( )

   -- Définition du streamId
   local streamId = rt.values[3]

    -- DATA non déclarées dans le SSC ignorées
   if( rt.streamInfos[ streamId ] == nil ) then
      return
   end

   -- Construction des nouveaux satellites
   if ( rt.objects[ streamId ] == nil ) then
      rt.objects[ streamId ] = {}
   end

   -- Découpage de la valeur reçue
   local streamData = {}
   local count = 0
   for word in string.gmatch( rt.values[4], "([^%s]+)" ) do
      streamData[ count ] = word
      count = count + 1
   end

   -- Création d'un nouvel enregistrement
   local nbRecords = 1 + # rt.objects[ streamId ]

   rt.objects[ streamId ][nbRecords] = {}

   -- Conversion et memorisation de la date des données
   rt.objects[ streamId ][nbRecords].t = jjCnes2jjCel( tonumber(rt.values[2]) )

   -- Mémorisation des valeurs de la position
   if( rt.streamInfos[ streamId ].dataType == "Position_t" ) then

      rt.objects[ streamId ][nbRecords].x = tonumber(streamData[ 0 ])
      rt.objects[ streamId ][nbRecords].y = tonumber(streamData[ 1 ])
      rt.objects[ streamId ][nbRecords].z = tonumber(streamData[ 2 ])

   elseif( rt.streamInfos[ streamId ].dataType == "Quaternion_t" ) then

      rt.objects[ streamId ][nbRecords].w = tonumber(streamData[ 0 ])
      rt.objects[ streamId ][nbRecords].x = tonumber(streamData[ 1 ])
      rt.objects[ streamId ][nbRecords].y = tonumber(streamData[ 2 ])
      rt.objects[ streamId ][nbRecords].z = tonumber(streamData[ 3 ])

   elseif( rt.streamInfos[ streamId ].dataType == "Angle_t" ) then

      -- On borne l'angle entre [-PI et PI]
      rt.objects[ streamId ][nbRecords].a =
         math.fmod( tonumber(streamData[ 0 ])/180*math.pi+math.pi, 2 * math.pi ) - math.pi

   elseif( rt.streamInfos[ streamId ].dataType == "Direction_t" ) then

      rt.objects[ streamId ][nbRecords].x = tonumber(streamData[ 0 ])
      rt.objects[ streamId ][nbRecords].y = tonumber(streamData[ 1 ])
      rt.objects[ streamId ][nbRecords].z = tonumber(streamData[ 2 ])

   else

      celestia:log( "Unknown streamId (" .. streamId .. ")" )

   end

end


--------------------------------------------------------------------------------
-- receiveTime
--
-- Traitement d'un paquet de temps
--------------------------------------------------------------------------------

function receiveTime( )

   -- Message de début d'animation au premier tick reçu
   if( firstTick == true ) then
      firstTick = false

      celestia:log( "Visualization ready!" )
   end

   -- Conversion et memorisation du temps simule en jours juliens CNES
   rt.pt = rt.t
   rt.t  = jjCnes2jjCel( tonumber(rt.values[2]) )
   if ( rt.pt == nil ) then
      rt.pt = rt.t
   end

   -- Ajustement du temps
   celestia:settime( rt.t )

   -- Ajustement de la vitesse Celestia
   if( rt.paused == false ) then
      celestia:settimescale( tonumber(rt.values[3]) )
   end

   -- Mise à jour de la texture si nécessaire
   if( hasAltTexture == true ) then

      -- Sélection du layer trouvé ou bein layer par défaut ("")
      local neededTex = ""
      if( currentLayer ~= nil ) then
         neededTex = currentLayer.getAltTexture( tonumber(rt.values[2]) )
      end
      -- Seulement si le layer est différent
      if( neededTex ~= currentTex ) then
         celestia:getobserver():setsurface( neededTex )
         currentTex = neededTex
      end
   end

end



--------------------------------------------------------------------------------
-- resetObserverFOV
--
-- Réinitialise le champ de vue de l'observeur avant les appels à des fonctions
-- de caméra. A appeler uniquement lorsque c'est nécessaire !
--------------------------------------------------------------------------------

function resetObserverFOV()

   -- Restauration de l'ouverture de la caméra potentiellement modifiée par une
   -- commande précédente
   setFov( 16 )

end


--------------------------------------------------------------------------------
-- stringToBoolean
--
-- Fonction utilitaire convertissant une chaîne de caractère true ou false
-- en booléen
-- param : chaîne de caractère
-- return true ou false
--------------------------------------------------------------------------------

function stringToBoolean( param )

   if( param == "true" ) then
      return true
   end
   return false
end


--------------------------------------------------------------------------------
-- receiveCmd
--
-- Traitement d'un paquet de commande
--------------------------------------------------------------------------------

function receiveCmd( )

   if( rt.values[2] == "STRUCT" ) then

      -- Visibilité des objets
      if( rt.values[3] == "Visible" ) then
         local object = getObjectFromFullName( rt.values[4], false )
         object:setvisible( stringToBoolean( rt.values[5] ) )

      -- Visibilité des objets (tout le satellite)
      elseif( rt.values[3] == "HierarchyVisible" ) then
         -- Récupération du ref de l'objet
         local object = getObjectFromFullName( rt.values[4], true )
         object:sethierarchyvisible( stringToBoolean( rt.values[5] ) )

      -- Visibilité de la grille planétaire (body)
      elseif( rt.values[3] == "PlanetographicGridVisible" ) then
         setPlanetographicGridVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité du terminateur (body)
      elseif( rt.values[3] == "TerminatorVisible" ) then
         setTerminatorVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité du cone d'ombre (body)
      elseif( rt.values[3] == "UmbraVisible" ) then
         setUmbraVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Couleur du cone d'ombre (body)
      elseif( rt.values[3] == "UmbraColor" ) then
         setUmbraColor( rt.values[4], rt.values[5] )

      -- Etendu du cone d'ombre (body)
      elseif( rt.values[3] == "UmbraExtent" ) then
         setUmbraExtent( rt.values[4], rt.values[5] )

      -- Visibilité du cone de pénombre (body)
      elseif( rt.values[3] == "PenumbraVisible" ) then
         setPenumbraVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Couleur du cone de pénombre (body)
      elseif( rt.values[3] == "PenumbraColor" ) then
         setPenumbraColor( rt.values[4], rt.values[5] )

      -- Etendu du cone de pénombre (body)
      elseif( rt.values[3] == "PenumbraExtent" ) then
         setPenumbraExtent( rt.values[4], rt.values[5] )

      -- Visibilité de toutes les ROI
      elseif( rt.values[3] == "AllRoiVisible" ) then
         setAllRoiVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité de toutes les ROI
      elseif( rt.values[3] == "AllPoiVisible" ) then
         setAllPoiVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité des orbites
      elseif( rt.values[3] == "TrackVisible" ) then
         setOrbitVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité des orbites
      elseif( rt.values[3] == "TrackWindow" ) then
         setOrbitWindow( rt.values[4], rt.values[5], rt.values[6] )

      -- Visibilité des axes EME2000
      elseif( rt.values[3] == "Eme2000AxesVisible" ) then
         setEme2000AxesVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité des axes QSW
      elseif( rt.values[3] == "QswAxesVisible" ) then
         setQswAxesVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité des axes TNW
      elseif( rt.values[3] == "TnwAxesVisible" ) then
         setTnwAxesVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité des axes
      elseif( rt.values[3] == "FrameAxesVisible" ) then
         setFrameAxesVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité de la direction soleil
      elseif( rt.values[3] == "SunDirectionVisible" ) then
         setSunDirectionVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité de la direction corps central
      elseif( rt.values[3] == "BodyDirectionVisible" ) then
         setBodyDirectionVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité du vecteur vitesse
      elseif( rt.values[3] == "VelocityVectorVisible" ) then
         setVelocityVectorVisible( rt.values[4], stringToBoolean( rt.values[5] ) )
      
     -- Visibilité des liens de satellite vers station
      elseif( rt.values[3] == "StationLinksVisible" ) then
         setVisibilityLinkVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité de l'ellipsoïde de la covariance en position
      elseif( rt.values[3] == "PositionalCovarianceVisible" ) then
         setPositionalCovarianceVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Couleur de l'ellipsoïde de la covariance en position
      elseif( rt.values[3] == "PositionalCovarianceColor" ) then
         setPositionalCovarianceColor( rt.values[4], rt.values[5] )

      -- Scale de l'ellipsoïde de la covariance en position
      elseif( rt.values[3] == "PositionalCovarianceScale" ) then
         setPositionalCovarianceScale( rt.values[4], rt.values[5] )

      -- Visibilité du cône de visée des senseurs
      elseif( rt.values[3] == "AimVolumeVisible" ) then
         setAimVolumeVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité du contour de visée des senseurs
      elseif( rt.values[3] == "AimContourVisible" ) then
         setAimContourVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité de l'axe de visée des senseurs
      elseif( rt.values[3] == "AimAxisVisible" ) then
         setAimAxisVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Visibilité de la tracedes senseurs
      elseif( rt.values[3] == "AimTraceVisible" ) then
         setAimTraceVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Facteur d'échelle d'un satellite
      elseif( rt.values[3] == "SatelliteScale" ) then
         magnifySatellite( getObjectNameFromFullName( rt.values[4] ), rt.values[5] )

      -- Facteur d'échelle d'un body
      elseif( rt.values[3] == "BodyScale" ) then
         magnifyBody( getObjectNameFromFullName( rt.values[4] ), rt.values[5] )

      -- Visibilité des ROI
      elseif( rt.values[3] == "RoiVisible" ) then
         setRoiVisible( getParentObjectNameFromFullName( rt.values[4] ),
                        getObjectNameFromFullName( rt.values[4] ),
                        stringToBoolean( rt.values[5] ) )

      -- Visibilité des labels des ROI
      elseif( rt.values[3] == "RoiTextVisible" ) then
         setRoiTextVisible( getParentObjectNameFromFullName( rt.values[4] ),
                            getObjectNameFromFullName( rt.values[4] ),
                            stringToBoolean( rt.values[5] ) )
      -- Visibilité des POI
      elseif( rt.values[3] == "PoiVisible" ) then
         setPoiVisible( getParentObjectNameFromFullName( rt.values[4] ),
                        getObjectNameFromFullName( rt.values[4] ),
                        stringToBoolean( rt.values[5] ) )

      -- Visibilité des labels des POI
      elseif( rt.values[3] == "PoiTextVisible" ) then
         setPoiTextVisible( getParentObjectNameFromFullName( rt.values[4] ),
                            getObjectNameFromFullName( rt.values[4] ),
                            stringToBoolean( rt.values[5] ) )

      -- Couleur des POI
      elseif( rt.values[3] == "PoiIconColor" ) then
         setPoiColor( getParentObjectNameFromFullName( rt.values[4] ),
                      getObjectNameFromFullName( rt.values[4] ),
                      rt.values[5] )

      -- Couleur des ROI
      elseif( rt.values[3] == "RoiContourColor" ) then
         setRoiColor( getParentObjectNameFromFullName( rt.values[4] ),
                      getObjectNameFromFullName( rt.values[4] ),
                      rt.values[5] )

      -- Epaisseur du contour des ROI
      elseif( rt.values[3] == "RoiContourWidth" ) then
         setRoiContourWidth( getParentObjectNameFromFullName( rt.values[4] ),
                             getObjectNameFromFullName( rt.values[4] ),
                             tonumber( rt.values[5] ) )

      -- Opacité des ROI
      elseif( rt.values[3] == "RoiFillOpacity" ) then
         setRoiOpacity( getParentObjectNameFromFullName( rt.values[4] ),
                        getObjectNameFromFullName( rt.values[4] ),
                        rt.values[5] )

      -- Visibilité du cluster
      elseif( rt.values[3] == "ClusterVisible" ) then
         setClusterVisible( rt.values[4], rt.values[5] )

      -- Symbol du cluster
      elseif( rt.values[3] == "ClusterSymbol" ) then
         setClusterSymbol( rt.values[4], rt.values[5] )

      -- Taille du symbole du cluster
      elseif( rt.values[3] == "ClusterSymbolSize" ) then
         setClusterSymbolSize( rt.values[4], tonumber( rt.values[5] ) )

     -- Visibilité des liens de satellite vers satellite
     elseif( rt.values[3] == "LinkVisible" ) then
        setVisualizerVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

     -- Visibilité des liens de satellite vers satellite
     elseif( rt.values[3] == "LayerVisible" ) then
        setLayerVisible( rt.values[4], stringToBoolean( rt.values[5] ) )

      -- Teste la reception d'une commande inconnue
      else
         celestia:log( "Unknown command of type STRUCT (" .. rt.values[3] .. ")" )

      end

      ------------------------
      -- Commandes spécifiques

   elseif( rt.values[2] == "PROP" ) then

      -- Offset de la caméra senseur
      if( rt.values[3] == "SensorCameraOffset" ) then
         rt.sensorCamOffset = -1 * tonumber( rt.values[4] ) / uly2m

      -- Position et taille de la fenêtre
      elseif( rt.values[3] == "WindowGeometry" ) then
         if( rt.values[4] ~= "Default" ) then
            celestia:setwindowinfos( tonumber(rt.values[4]),
                                     tonumber(rt.values[5]),
                                     tonumber(rt.values[6]),
                                     tonumber(rt.values[7]) )
         end

      -- Grille équatoriale
      elseif( rt.values[3] == "equatorialgrid" ) then
         celestia:setrenderflags{equatorialgrid = stringToBoolean( rt.values[4] ) }

      -- Plan équatorial
      elseif( rt.values[3] == "equatorialplan" ) then
         celestia:setrenderflags{equatorialplan = stringToBoolean( rt.values[4] ) }

      -- Caméra manuelle
      elseif( rt.values[3] == "CameraDesc" ) then
         if( rt.values[4] == "Default" ) then
            -- Caméra centrée sur le 1er satellite par défaut
            if( #gSatellites > 0 ) then
               local satelliteName = gSatellites[1].name
               local centralBodyPath = gSatellites[1].centralBodyName
               setCameraSynchronous( centralBodyPath.."/"..satelliteName.."_ref/"..satelliteName,
                                     centralBodyPath.."/"..satelliteName.."_ref/"..satelliteName.."_QswAxes",
                                     "YZX", 1 )
            else
               -- Si pas de satellite, on zoom sur le premier Body
               if( #gBodies > 0 ) then
                  local centralBodyPath = gBodies[1].name
                  setCameraSynchronous( centralBodyPath,
                                        centralBodyPath.."/"..centralBodyPath.."_Axes",
                                        "+X", 0.33 )
               else
                  -- Il ne reste plus que le SOleil
                  setCameraSynchronous( "Sol", "Sol/Sol_Axes", "+X", 0.33 )
               end
            end
         elseif( rt.values[4] == "multi" ) then
            -- Multiview
            createMultiView( tonumber(rt.values[5]), tonumber(rt.values[6]) )
            -- Parcours des caméras
            local i = 0
            local j = 0
            for cam in string.gmatch( rt.lastMessage, "[^" .. rt.multi.sep .. "]+" ) do
               -- Saut du premier élément
               if( j ~= 0 ) then
                  -- Extraction des paramètres de la caméra
                  cam = lineSplit( cam )
                 local camInfo = ""
                  if( # cam == 12 ) then
                     camInfo = cam[12]
                  end
                  -- Application des paramètres de caméra
                  setCustomCamera( i, j, cam[1], cam[2], cam[3],
                                   cam[4], cam[5], cam[6],
                                   cam[7], cam[8], cam [9],
                                   cam[10], cam[11], camInfo )
               end
               -- Vue suivante
               j = j % rt.multi.v + 1
               if( j == 1 ) then
                  i = i + 1
               end
            end
         else
            -- Singleview
            createMultiView( 1, 1 )
            local camInfo = ""
            if( # rt.values == 15 ) then
               camInfo = rt.values[15]
            end
            -- Application des paramètres de caméra
            setCustomCamera( 1, 1, rt.values[4], rt.values[5], rt.values[6],
                             rt.values[7], rt.values[8], rt.values[9],
                             rt.values[10], rt.values[11], rt.values[12],
                             rt.values[13], rt.values[14], camInfo )
         end

      -- Sélection d'objet
      elseif( rt.values[3] == "SelectObject" ) then
         -- Détection d'une chaîne vide ou "?" sélection vide par défaut
         if( rt.values[4] == '?' or rt.values[4] == "" or rt.values[4] == nil ) then
            -- Désélection
            celestia:select( celestia:find( "" ) )
         else
            -- Récupération et sélection de l'objet
            local obj = getObjectFromFullName( rt.values[4], false )
            if( not(empty(obj)) ) then
               celestia:select( obj )
            end
         end

      -- Visibilité des labels satellites
      elseif( rt.values[3] == "SatelliteLabelsVisible" ) then
         celestia:setlabelflags{ spacecraft = stringToBoolean( rt.values[4] ) }

      -- Scale du système solaire
      elseif( rt.values[3] == "SolarSystemScale" ) then
         -- Facteur d'échelle des planètes du système solaire traité en continu
         -- dans le hook principal
         local solarMagCoeff = rt.values[4]
         setSolarMagnification( solarMagCoeff )

      -- Affichage des menus
      elseif( rt.values[3] == "WindowMenus" ) then
         local boolean = stringToBoolean( rt.values[4] )
         celestia:setwindowmenuvisible( boolean )

      -- Affichage des informations
      elseif( rt.values[3] == "WindowText" ) then
         local boolean = stringToBoolean( rt.values[4] )
         celestia:sethuddetail(1)
         celestia:setoverlayelements{Time = boolean, Velocity = boolean, Selection = boolean, Frame = boolean}

      -- Lumière ambiante
      elseif( rt.values[3] == "AmbientLight" ) then
         celestia:setambient( tonumber( rt.values[4] ) )

      -- Visibilité du top layer affecté au layer des nuages de Celestia
      elseif( rt.values[3] == "OverlayLayerVisible" ) then
         celestia:setrenderflags{ cloudmaps = stringToBoolean( rt.values[4] ) }

      -- Ombres portées
      elseif( rt.values[3] == "Shadows" ) then
         celestia:setrenderflags{shadows = stringToBoolean( rt.values[4] ) }

      -- Teste la reception d'une commande inconnue
      else
         celestia:log( "Unknown command of type PROP (" .. rt.values[3] .. ")" )

      end

   ---------------------------
   -- Commandes générales

   elseif( rt.values[2] == "SERVICE" ) then

      -- Demande de sauvegarde du paramétrage
      if( rt.values[3] == "SaveState" ) then

         -- Render Flags (equatorial grid)
         local actualRenderflags = celestia:getrenderflags()
         local stringToSend = "CMD PROP equatorialgrid " .. tostring( actualRenderflags.equatorialgrid )
         rt.client:send( "CMD SERVICE StoreCommand " .. stringToSend .. "\n" )

         -- Render Flags (Shadows, Cosmographia only)
         if( actualRenderflags.shadows ~= nil ) then
            local stringToSend = "CMD PROP Shadows " .. tostring( actualRenderflags.shadows )
            rt.client:send( "CMD SERVICE StoreCommand " .. stringToSend .. "\n" )
         end

         -- Caméra courante
         local stringToSend = "CMD PROP CameraDesc " .. getCameraToString()
         rt.client:send( "CMD SERVICE StoreCommand " .. stringToSend .. "\n" )

         -- Sélection
         local stringToSend = "CMD PROP SelectObject \"" .. getCelestiaFullName( celestia:getselection() ) .. "\""
         rt.client:send( "CMD SERVICE StoreCommand " .. stringToSend .. "\n" )

         -- Lumière ambiante
         local stringToSend = "CMD PROP AmbientLight \"" .. celestia:getambient() .. "\""
         rt.client:send( "CMD SERVICE StoreCommand " .. stringToSend .. "\n" )

         -- Labels satellites
         local actuallabelflags = celestia:getlabelflags()
         local stringToSend = "CMD PROP SatelliteLabelsVisible " .. tostring( actuallabelflags.spacecraft )
         rt.client:send( "CMD SERVICE StoreCommand " .. stringToSend .. "\n" )

         -- Sauvegarde terminée
         rt.client:send( "CMD SERVICE SaveStateFinished\n" )

      -- Définition du corps de démarrage
      elseif( rt.values[3] == "InitCentralBody" ) then
         local bodyName = rt.values[4]
         initBody( bodyName )

      -- Définition du satellite de démarrage
      elseif( rt.values[3] == "InitSatellite" ) then
         local satName = rt.values[4]
         local parentPath = rt.values[5]
         initSatellite( satName, parentPath )

      -- Récupération de la caméra
      elseif( rt.values[3] == "SaveCamera" ) then
         local cameraDescString = "CMD PROP CameraDesc " .. getCameraToString()
         rt.client:send( "CMD SERVICE StoreCommand " .. cameraDescString .. "\n" )

      -- Récupération de la position de la fenêtre
      elseif( rt.values[3] == "SaveWindow" ) then
         local xPos, yPos, width, height = celestia:getwindowinfos()
         local stringToSend = "CMD PROP WindowGeometry " .. math.floor(xPos) .. " " .. math.floor(yPos) .. " " .. math.floor(width) .. " " .. math.floor(height)
         rt.client:send( "CMD SERVICE StoreCommand " .. stringToSend .. "\n" )

      -- TakeScreenshot <filename>
      elseif( rt.values[3] == "TakeScreenshot" ) then
         celestia:takescreenshot( "vts", rt.values[4] )

      -- CaptureZbuffer <filename>
      elseif( rt.values[3] == "CaptureZbuffer" ) then
         celestia:takescreenshot( "zbuff", rt.values[4] )

      -- CaptureBuffer
      elseif( rt.values[3] == "CaptureBuffer" ) then
      captureAndStreamBuffer( rt.values[4], ( rt.values[5] == "FWD" ), rt.values[6] )

      -- Demande de synchro
      elseif( rt.values[3] == "SynchroRequested" ) then

         -- Après un paquet de commande, on sort forcément de Lua pour s'assurer
         -- qu'elle ait bien été exécutée
         rt.sendSync = true
         return false

      -- Teste la reception d'une commande inconnue
      else
         celestia:log( "Unknown command of type SERVICE (" .. rt.values[3] .. ")" )

      end


   ---------------------------
   -- Commandes de temps

   elseif( rt.values[2] == "TIME" ) then

      -- Arret de la simulation
      if( rt.values[3] == "PAUSE" ) then
         rt.paused = true
         celestia:settimescale( 0 )

      -- Reprise de la simulation
      elseif( rt.values[3] == "PLAY" ) then
         rt.paused = false

      -- Teste la reception d'une commande inconnue
      else
         celestia:log( "Unknown command of type TIME (" .. rt.values[3] .. ")" )

      end


   ---------------------------
   -- Commandes de caméras

   elseif( rt.values[2] == "CAMERA" ) then

      -- Selection de l'objet
      if( rt.values[3] == "Select" ) then
         local objectName = rt.values[4]
         local objectSelect = celestia:find( objectName )
         celestia:select(objectSelect)
         -- On retient le nom de l'objet sélectionné
         lastSelectedObject = objectSelect

      -- Caméra synchrone (= bodyfixed) avec l'objet et sélection
      elseif( rt.values[3] == "Synchronous" ) then
         resetObserverFOV()
         local distanceFactor = 1
         if( rt.values[7] ~= nil ) then
            distanceFactor = rt.values[7]
         end
         setCameraSynchronous( rt.values[4], rt.values[5], rt.values[6], distanceFactor )

      -- Caméra inertielle (= ecliptic) avec l'objet et sélection
      elseif( rt.values[3] == "Follow" ) then
         resetObserverFOV()
         local objectName = rt.values[4]
         local objectSelect = celestia:find( objectName )
         celestia:select(objectSelect)
         if( objectSelect:type() == "null" ) then
            return
         end
         -- Synchronisation de l'orbite avec l'objet
         celestia:getobserver():follow(objectSelect)
         -- On retient le nom de l'objet sélectionné
         lastSelectedObject = objectSelect

      -- Camera GotoSatellite
      elseif( rt.values[3] == "Goto" ) then
         resetObserverFOV()
         setCameraGoto( rt.values[4] )

      -- Camera CenterSatellite
      elseif( rt.values[3] == "Center" ) then
         resetObserverFOV()
         setCameraCenter( rt.values[4] )

      -- Camera CameraBodyToBody
      elseif( rt.values[3] == "CameraSatToBody" ) then
         resetObserverFOV()
         local camDirection = rt.values[4]
         local satelliteName = rt.values[5]
         local bodyName = rt.values[6]
         setCameraBodyToBody( camDirection, satelliteName, bodyName )

      -- Camera CameraOrbitSat
      elseif( rt.values[3] == "CameraOrbitSat" ) then
         resetObserverFOV()
         local opposite = false
         if( rt.values[5] ~= nil and rt.values[5] == "Opposite" ) then
            opposite = true
         end
         setCameraOrbit( rt.values[4], opposite )

      -- Camera Camera_SensorView
      elseif( rt.values[3] == "CameraSensorView" or rt.values[3] == "Camera_SensorView") then
         resetObserverFOV()

         -- Caméra pointée vers Z (repère Celestia)
         local toVector = celestia:newvector( 0, 0, -1 )

         -- Le UP par défaut est X
         local upName = "X"
         if( rt.values[7] ~= nil ) then
            upName = rt.values[7]
         end

         -- Vecteur Up de la caméra
         local upVector
         if( upName == "X" ) then
            upVector = celestia:newvector( -1, 0, 0 )
         else
            upVector = celestia:newvector( 0, 1, 0 )
         end

         setCameraSensorView( rt.values[4], rt.values[5], rt.values[6], toVector, upVector, upName, 5 )

      -- Camera Camera_SensorView
      elseif( rt.values[3] == "CameraAzimuthElevation" ) then
         resetObserverFOV()

         local sensorFullName = rt.values[4]

         -- Direction et up
         local minAzimuth   = rt.values[5]
         local maxAzimuth   = rt.values[6]
         local minElevation = rt.values[7]
         local maxElevation = rt.values[8]

         -- Le UP par défaut est X
         local upName = "X"
         if( rt.values[9] ~= nil ) then
            upName = rt.values[9]
         end

         -- Vecteur Up
         local upVector
         if( upName == "X" ) then
            upVector = celestia:newvector( 0, 0, -1 )
         else
            upVector = celestia:newvector( 0, 1, 0 )
         end

         local dirVector = celestia:newvector( -1, 0, 0 )

         -- Rotation azimuth/elevation
         local q1 = celestia:newrotation(celestia:newvector(0, 0, -1), minAzimuth + (maxAzimuth-minAzimuth)/2)
         local q2 = celestia:newrotation(celestia:newvector(0, 1, 0), minElevation + (maxElevation-minElevation)/2)
         local camRotation = multQuat(q2, q1)

         -- Calcul du directeur et vecteur up
         local rotatedDirVector = camRotation:transform( dirVector ):normalize()
         local rotatedUpVector = camRotation:transform( upVector ):normalize()

         -- Caméra
         setCameraSensorView( rt.values[4], (maxAzimuth-minAzimuth)/2, (maxElevation-minElevation)/2, rotatedDirVector, rotatedUpVector, upName, 5 )

      -- Camera WindowSensorView
      elseif( rt.values[3] == "WindowSensorView" ) then
         resetObserverFOV()

         -- Le UP par défaut est X
         local up = "X"
         if( rt.values[8] ~= nil ) then
            up = rt.values[8]
         end
          setWindowSensorView( rt.values[4], rt.values[5], rt.values[6], rt.values[7], up )

      -- Multiview
      elseif( rt.values[3] == "CreateMultiView" ) then
         createMultiView( rt.values[4], rt.values[5] )

      -- Sélection de vue
      elseif( rt.values[3] == "SelectView" ) then
         local obs = celestia:getobservers()[ getObserverIndex( rt.values[4], rt.values[5] ) ]
         if( obs ~= nil ) then
            obs:makeactiveview()
         end

      -- Fov
      elseif( rt.values[3] == "SetFov" ) then
         setFov( rt.values[4] )

      -- Teste la reception d'une commande inconnue
      else
         celestia:log( "Unknown command of type CAMERA (" .. rt.values[3] .. ")" )

      end

   ---------------------------
   -- Autres trucs

   -- Affichage d'un message
   elseif( rt.values[2] == "FlashText" ) then
      celestia:flash( rt.values[3] )

   -- Camera RotateQuatCamera
   elseif( rt.values[2] == "RotateQuatCamera" ) then
      rotateQuatCameraAndroid( rt.values[3], rt.values[4], rt.values[5], rt.values[6], rt.values[7] )

   -- Camera GotoHome
   elseif( rt.values[2] == "GotoHome" ) then
      -- On se rend au dernier objet sélectionné
      setCameraHome( lastSelectedObject)

   -- Camera SetAndGotoHome
   elseif( rt.values[2] == "SetAndGotoHome" ) then
      -- On se rend à l'objet sélectionné
      local homeObject = celestia:find( rt.values[2] )
      if( homeObject:type() == "null" ) then
         return
      end
      setCameraHome( homeObject )


   -- Teste la reception d'une commande inconnue
   else
      celestia:log( "Unknown command (" .. rt.values[2] .. ")" )
   end

   return true

end


--------------------------------- End Of File ----------------------------------

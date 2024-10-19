--------------------------------------------------------------------------------
-- vtsAndroid.lua
--
-- HISTORIQUE
-- VERSION : 3.4 : DM : DM_498 : 10/12/2019 : Définition des champs de vue via Azimut et Elévation
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Textures dynamiques )
-- VERSION : 2.0 : DM : VTS 2012 : 23/07/2012 : VTS 2.0 ( DM 36 - Conservation du parametrage )
-- VERSION : 1.2.2 : DM : CELEST-SB-DOC-3361-CN : 14/04/2011 : Complément VTS 2010
-- FIN-HISTORIQUE
-- Copyright © (2019) CNES All rights reserved
--
-- VER : $Id: vtsAndroid.lua 8148 2019-12-10 10:46:01Z mmo $
--------------------------------------------------------------------------------


--------------------------------------------------------------------------------
-- rotateQuatCameraAndroid
-- Fonction appelée à chaque réception de packet TCP de rotation de la caméra
--------------------------------------------------------------------------------

function rotateQuatCameraAndroid( sensorW, sensorX, sensorY, sensorZ, sensorTime )
   curCamRot = aCamera:getorientation()
   curSensRot = celestia:newrotation( sensorW, sensorX, sensorY, sensorZ ) 
   
   lastDataTime = os.clock()
   
   if( aCameraInit == true ) then 
      
      -- Initialisations date/quaternion
      firstSensorTime = sensorTime
      firstCelTime = lastDataTime

      -- Initialialisation des quaternions de référence
      -- Quaternion de référence du capteur
      referenceSensRot = curSensRot
      
      -- Quaternion de référence de la caméra
      referenceCamRot = aCamera:getorientation()
      
      -- Précalcul du quaternion de passage de Senseur->Camera : QsensRef^-1*QcamRef
      sensToCamRot = multQuat( invQuat( referenceSensRot ), referenceCamRot )
      
      -- Calcul de la rotation du senseur dans le repère caméra
      slerpRot = multQuat( curSensRot, sensToCamRot )

      -- Flags
      aCameraInit = false
      boolHookCodeAndroid = false   
      
   else
      -- Appel à chaque paquet réseau
      
      -- Temps de l'accelerometre dans le repère temporel Celestia
      endTime = sensorTime - firstSensorTime + firstCelTime 
      
      -- Correction si on dépasse la date courante
      if (endTime > lastDataTime) then
         firstCelTime = firstCelTime - endTime + lastDataTime
         endTime = sensorTime - firstSensorTime + firstCelTime
      end
      
      -- Ajout d'un paramètre utilisateur smooth
      endTime = endTime + 0.5
      
      startTime = lastDataTime
      startRot = slerpRot      

      -- Calcul de la rotation du senseur dans le repère caméra
      endRot = multQuat( curSensRot, sensToCamRot )
      
      boolHookCodeAndroid = true
   end
end


--------------------------------------------------------------------------------
-- hookTestCodeAndroid
-- Fonction appelée à chaque rendu de l'image de Celestia si demandé
--------------------------------------------------------------------------------

function hookTestCodeAndroid( dt )

   curTime = os.clock()
   
   if( curTime - endTime > 0 ) then
      -- Plus de données depuis 1 seconde : Celestia en idle ou perte connexion
      if( (curTime - endTime > 1) or ( curTime - lastDataTime > 1) ) then    
         -- Stoppe l'interpolation
         boolHookCodeAndroid = false
         -- Demande de réinit
         aCameraInit = true
         -- On se position à la date de fin pour arrêter d'interpoler
         curTime = endTime
      else 
         if( curTime - lastDataTime > 0 ) then
            -- On met à jour la fenêtre d'interpolation
            endTime = curTime + 0.5
            startTime = curTime
            startRot = slerpRot
         end
      end
   end
   
   -- Interpolation
   slerpRot = startRot:slerp( endRot, (curTime-startTime) / (endTime-startTime) )
   aCamera:setorientation( slerpRot )
end


--------------------------------- End Of File ----------------------------------

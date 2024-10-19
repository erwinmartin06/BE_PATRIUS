--------------------------------------------------------------------------------
-- vtsOrbit.lua
--
-- HISTORIQUE
-- VERSION : 3.4 : DM : DM_498 : 10/12/2019 : Définition des champs de vue via Azimut et Elévation
-- VERSION : 3.2 : FA : FA_448 : 07/12/2017 : Bug temps-réel : vecteur vitesse erroné
-- VERSION : 2.7 : FA : FA_255 : 15/07/2015 : Problème de "sauts" de la camèra TSW en temps-réel
-- VERSION : 2.0 : DM : VTS 2012 : 23/07/2012 : VTS 2.0 ( DM 36 - Conservation du parametrage )
-- VERSION : 2.0 : DM : VTS 2012 : 23/07/2012 : VTS 2.0 ( DM 38 - Temps-reel )
-- VERSION : 1.3.0 : DM : CELEST-SB-DOC-3361-CN : 27/02/2012 : VTS 1.3.0 ( VTS 2011 )
-- VERSION : 1.2.2 : DM : CELEST-SB-DOC-3361-CN : 14/04/2011 : Complément VTS 2010
-- FIN-HISTORIQUE
-- Copyright © (2019) CNES All rights reserved
--
-- VER : $Id: vtsOrbit.lua 8148 2019-12-10 10:46:01Z mmo $
--------------------------------------------------------------------------------


--------------------------------------------------------------------------------
-- ???
--------------------------------------------------------------------------------

function newinstance(class, o)
   o = o or {}
   setmetatable(o, class)
   class.__index = class
end


--------------------------------------------------------------------------------
-- getSurroundingValues
--
-- Retourne les valeurs r�elles encadrant la date courante
--
-- Param�tres:
--   streamBuffer :     Buffer d'objets d'un stream
--   currentDate  :     Date courante
--
-- Retour:
--   Taille du buffer
--   Index de l'�l�ment pr�c�dent
--   Index de l'�l�ment suivant
--   Position dans la fenetre d'interpolation (t)
--------------------------------------------------------------------------------

function getSurroundingValues( streamBuffer, currentDate )

   local nbRecords = # streamBuffer

   -- Pr�caution s'il n'y a aucune donn�e
   if( nbRecords == 0 ) then
      return 0, 0, 0, 0
   end

   -- Recherche de donn�es correspondant � la date courante (recherche depuis la fin)
   local from = nbRecords
   while( from > 1 and currentDate < streamBuffer[from].t ) do
      from = from - 1
   end

   -- D�finition des limites
   local to = 0
   if( from >= 1 and from < nbRecords ) then
      -- Cas nominal
      to = from + 1
   else
      -- Avant les premi�res donn�es ou apr�s les derni�res
      to = from
   end

   -- Calcul de la position dans la fenetre d'interpolation
   local t
   if ( streamBuffer[to].t ~= streamBuffer[from].t ) then
      t = ( currentDate - streamBuffer[from].t ) /
          ( streamBuffer[to].t - streamBuffer[from].t )
      if( t > 1 ) then t = 1
      elseif( t < 0 ) then t = 0
      end
   else
      t = 0
   end

   -- R�sultats
   return nbRecords, from, to, t

end


--------------------------------------------------------------------------------
-- cleanBuffer
--
-- Nettoie le buffer des valeurs inutilis�es avant l'index en param�tre
--
-- Param�tres:
--   streamBuffer :     Buffer d'objets d'un stream
--   fromIndex    :     Index avant lequel on doit nettoyer le buffer
--------------------------------------------------------------------------------

function cleanBuffer( streamBuffer, fromIndex )

   -- Nettoyage du buffer
   if( fromIndex > 1 ) then
      for i = fromIndex-1, 1, -1 do
         table.remove( streamBuffer, i )
      end
   end

end


--------------------------------------------------------------------------------
-- getCelestiaQuat
--
-- Exprime un quaternion dans le rep�re de Celestia
--
--
-- Param�tres :
--   qw, qx, qy, qz :     quaternion d'origine
--   objectType     :     type d'objet � orienter (satellite ou component)
--
-- Retour :
--   Quaternion exprim� dans le rep�re correspondant dans Celestia
--------------------------------------------------------------------------------
 
function getCelestiaQuat( qw, qx, qy, qz, objectType )

   local cw, cx, cy, cz

   if( objectType == "satellite" ) then

      -- Rotation de -Pi/2 selon X (AC)
      local r2s2 = math.sqrt( 2 ) / 2 ;
      cw = -qx * r2s2 - qw * r2s2
      cx =  qx * r2s2 - qw * r2s2
      cy =  qy * r2s2 + qz * r2s2
      cz = -qy * r2s2 + qz * r2s2

   else

      -- Rotation de 180 selon Y (conjugate du r�sultat AC)
      cw =  qy
      cx =  qz
      cy =  qw
      cz = -qx

   end

   return cw, cx, cy, cz

end


--------------------------------------------------------------------------------
-- Fonction appelee par Celestia a l'initialisation
--------------------------------------------------------------------------------

function rtposition( t )

   local orbit = {} ;

   -- Stockage d'informations sp�cifiques � l'objet
   orbit.boundingRadius = t.BoundingSphereRadius
   orbit.streamId       = t.StreamId
   orbit.valueType      = t.ValueType

   -- Stockage d'informations sp�cifiques au stream
   rt.streamInfos[ t.StreamId ] = {}
   rt.streamInfos[ t.StreamId ].dataType = "Position_t"

   -----------------------------------------------------------------------------
   -- Fonction appelee par Celestia a chaque affichage
   --
   -- ATTENTION : appel�e aussi � TJD + 1 minute pour le calcul de la vitesse
   -- par Celestia. Dans ce cas, on renvoit le dernier point disponible !
   -----------------------------------------------------------------------------

   function orbit:position( tjd )

      -- R�cup�ration des valeurs encadrant la date courante
      local nbRecords, from, to, t = getSurroundingValues( rt.objects[ orbit.streamId ], tjd )

      -- Pr�caution s'il n'y a aucune donn�e
      if( nbRecords == 0 ) then
         return 0, 0, 0
      end

      -- Simplification d'�criture (attention : r�f�rences, � consid�rer comme des const)
      local fromValue = rt.objects[ orbit.streamId ][from]
      local toValue = rt.objects[ orbit.streamId ][to]

      -- S�lectionne s'il y a besoin d'interpolation
      local x, y, z
      if( orbit.valueType == "INTERPOL" ) then

         -- Interpolation lineaire
         normFrom = math.sqrt( fromValue.x * fromValue.x + fromValue.y * fromValue.y + fromValue.z * fromValue.z )
         normTo = math.sqrt( toValue.x * toValue.x + toValue.y * toValue.y + toValue.z * toValue.z )
         normT = normFrom + (normTo - normFrom) * t
         x = (fromValue.x/normFrom + (toValue.x/normTo - fromValue.x/normFrom) * t)
         y = (fromValue.y/normFrom + (toValue.y/normTo - fromValue.y/normFrom) * t)
         z = (fromValue.z/normFrom + (toValue.z/normTo - fromValue.z/normFrom) * t)
         normUnit = math.sqrt( x*x + y*y + z*z )
         x = x / normUnit * normT
         y = y / normUnit * normT
         z = z / normUnit * normT

      else

         -- Pas besoin d'interpoler (mode DIRECT)
         if( from > 1 and tjd < celestia:gettime() ) then
            -- Cas d'une position pour un vecteur vitesse
            local fromValueVelocity = rt.objects[ orbit.streamId ][ from - 1 ]
            x = fromValueVelocity.x
            y = fromValueVelocity.y
            z = fromValueVelocity.z
         else
            x = fromValue.x
            y = fromValue.y
            z = fromValue.z
         end

      end

      -- Nettoyage du buffer (on garde un donn�e suppl�mentaire pour le vecteur vitesse)
      cleanBuffer( rt.objects[ orbit.streamId ], from - 1 )

      return x, y, z

    end

    -- Renvoie l'objet utilise ensuite par Celestia
    return orbit

end


--------------------------------------------------------------------------------
-- Fonction appelee par Celestia a l'initialisation
--------------------------------------------------------------------------------

function rtquaternion(t)

   local localRot = {};

   -- Stockage d'informations sp�cifiques � l'objet
   localRot.streamId   = t.StreamId
   localRot.objectType = t.ObjectType
   localRot.valueType  = t.ValueType

   -- Stockage d'informations sp�cifiques au stream
   rt.streamInfos[ t.StreamId ] = {}
   rt.streamInfos[ t.StreamId ].dataType = "Quaternion_t"

   -----------------------------------------------------------------------------
   -- Fonction appelee par Celestia a chaque affichage
   -----------------------------------------------------------------------------

   function localRot:orientation( tjd )

      -- R�cup�ration des valeurs encadrant la date courante
      local nbRecords, from, to, t = getSurroundingValues( rt.objects[ localRot.streamId ], tjd )

      -- Pr�caution s'il n'y a aucune donn�e
      if( nbRecords == 0 ) then
         return 1, 0, 0, 0
      end

      -- Simplification d'�criture (attention : r�f�rences, � consid�rer comme des const)
      local fromValue = rt.objects[ localRot.streamId ][from]
      local toValue = rt.objects[ localRot.streamId ][to]

      -- S�lectionne s'il y a besoin d'interpolation
      local qw, qx, qy, qz
      if( localRot.valueType == "INTERPOL" ) then

         local fromW, fromX, fromY, fromZ
         local toW, toX, toY, toZ

         -- Sauvegarde locale pour inversion (toW) et lecture plus facile
         fromW = fromValue.w
         fromX = fromValue.x
         fromY = fromValue.y
         fromZ = fromValue.z
         toW = toValue.w
         toX = toValue.x
         toY = toValue.y
         toZ = toValue.z

         -- Calcul de l'angle entre les deux quaternions
         local cosHalfTheta = toW*fromW + toX*fromX + toY*fromY + toZ*fromZ

         -- Si l'angle est tres petit (arrondi), pas d'interpolation
         if( math.abs( cosHalfTheta ) >= 1 ) then

            qw = fromW
            qx = fromX
            qy = fromY
            qz = fromZ

         else

            -- Choisi le chemin le plus court
            if( cosHalfTheta < 0 ) then
               toW = -toW
               toX = -toX
               toY = -toY
               toZ = -toZ
               cosHalfTheta = -cosHalfTheta;
            end

            -- Precalculs pour l'interpolation
            local halfTheta = math.acos( cosHalfTheta )
            local sinHalfTheta = math.sqrt( 1.0 - cosHalfTheta*cosHalfTheta ) ;
            local ratioA = math.sin( (1 - t) * halfTheta ) / sinHalfTheta ;
            local ratioB = math.sin( t * halfTheta ) / sinHalfTheta ;

            -- Interpolation
            qw = fromW * ratioA + toW * ratioB
            qx = fromX * ratioA + toX * ratioB
            qy = fromY * ratioA + toY * ratioB
            qz = fromZ * ratioA + toZ * ratioB

         end

      else

         -- Pas besoin d'interpoler (mode DIRECT)
         qw = fromValue.w
         qx = fromValue.x
         qy = fromValue.y
         qz = fromValue.z

      end

      -- Passage dans le rep�re de Celestia
      local cw, cx, cy, cz
      cw, cx, cy, cz = getCelestiaQuat( qw, qx, qy, qz, localRot.objectType ) ;

      -- Nettoyage du buffer
      cleanBuffer( rt.objects[ localRot.streamId ], from )

      return cw, cx, cy, cz

   end

    -- Renvoie l'objet utilise ensuite par Celestia
    return localRot

end


--------------------------------------------------------------------------------
-- Fonction appelee par Celestia a l'initialisation
--------------------------------------------------------------------------------

function rtangle(t)

   local localAngle = {};

   -- Stockage d'informations sp�cifiques � l'objet
   localAngle.streamId   = t.StreamId
   localAngle.objectType = t.ObjectType
   localAngle.valueType  = t.ValueType
   localAngle.axisX      = t.AxisX
   localAngle.axisY      = t.AxisY
   localAngle.axisZ      = t.AxisZ

   -- Normalisation de l'axe
   local norme = math.sqrt( localAngle.axisX * localAngle.axisX + localAngle.axisY * localAngle.axisY + localAngle.axisZ * localAngle.axisZ )
   localAngle.axisX = localAngle.axisX / norme
   localAngle.axisY = localAngle.axisY / norme
   localAngle.axisZ = localAngle.axisZ / norme

   -- Stockage d'informations sp�cifiques au stream
   rt.streamInfos[ t.StreamId ] = {}
   rt.streamInfos[ t.StreamId ].dataType = "Angle_t"

   -----------------------------------------------------------------------------
   -- Fonction appelee par Celestia a chaque affichage
   -----------------------------------------------------------------------------

   function localAngle:orientation( tjd )

      -- R�cup�ration des valeurs encadrant la date courante
      local nbRecords, from, to, t = getSurroundingValues( rt.objects[ localAngle.streamId ], tjd )

      -- Pr�caution s'il n'y a aucune donn�e
      if( nbRecords == 0 ) then
         return 0
      end

      -- Copie locale des angles (ils peuvent �tre manipul�s)
      local fromValueAngle = rt.objects[ localAngle.streamId ][from].a
      local toValueAngle = rt.objects[ localAngle.streamId ][to].a

      -- S�lectionne s'il y a besoin d'interpolation
      local angle
      if( localAngle.valueType == "INTERPOL" ) then

          -- D�tection de d�bordement
         if( math.abs( toValueAngle - fromValueAngle ) > math.pi ) then
            if( fromValueAngle < toValueAngle ) then
               fromValueAngle = fromValueAngle + 2 * math.pi
            else
               toValueAngle = toValueAngle + 2 * math.pi
            end
         end

         -- Interpolation lin�aire
         angle = fromValueAngle + (toValueAngle - fromValueAngle) * t

         -- Recentrage
         if( angle >= math.pi / 2 ) then
            angle = angle - 2 * math.pi
         end

      else

         -- Pas besoin d'interpoler (mode DIRECT)
         angle = fromValueAngle

      end

      local qw, qx, qy, qz
      qw = math.cos( angle / 2 )
      qx = localAngle.axisX * math.sin( angle / 2 )
      qy = localAngle.axisY * math.sin( angle / 2 )
      qz = localAngle.axisZ * math.sin( angle / 2 )

      -- Passage dans le rep�re de Celestia
      local cw, cx, cy, cz
      cw, cx, cy, cz = getCelestiaQuat( qw, qx, qy, qz, localAngle.objectType ) ;

      -- Nettoyage du buffer
      cleanBuffer( rt.objects[ localAngle.streamId ], from )

      return cw, cx, cy, cz

      end

    -- Renvoie l'objet utilise ensuite par Celestia
    return localAngle

end


--------------------------------------------------------------------------------
-- Fonction appelee par Celestia a l'initialisation
--------------------------------------------------------------------------------

function rtdirection(t)

   local localDir = {};

   -- Stockage d'informations sp�cifiques � l'objet
   localDir.streamId = t.StreamId
   localDir.objectType = t.ObjectType
   localDir.valueType  = t.ValueType

   -- Stockage d'informations sp�cifiques au stream
   rt.streamInfos[ t.StreamId ] = {}
   rt.streamInfos[ t.StreamId ].dataType = "Direction_t"

   -----------------------------------------------------------------------------
   -- Fonction appelee par Celestia a chaque affichage
   -----------------------------------------------------------------------------

   function localDir:orientation( tjd )

      -- R�cup�ration des valeurs encadrant la date courante
      local nbRecords, from, to, t = getSurroundingValues( rt.objects[ localDir.streamId ], tjd )

      -- Pr�caution s'il n'y a aucune donn�e
      if( nbRecords == 0 ) then
         return 1, 0, 0, 0
      end

      -- Simplification d'�criture
      local fromValue = rt.objects[ localDir.streamId ][from]
      local toValue = rt.objects[ localDir.streamId ][to]

      -- S�lectionne s'il y a besoin d'interpolation
      local x, y, z
     if( localDir.valueType == "INTERPOL" ) then

         -- Interpolation lin�aire
         x = fromValue.x + (toValue.x - fromValue.x) * t
         y = fromValue.y + (toValue.y - fromValue.y) * t
         z = fromValue.z + (toValue.z - fromValue.z) * t

         else

         -- Pas besoin d'interpoler (mode DIRECT)
         x = fromValue.x
         y = fromValue.y
         z = fromValue.z

      end

      -- Normalisation
      local norme = math.sqrt( x * x + y * y + z * z )
      local nx = x / norme
      local ny = y / norme
      local nz = z / norme

      local qw, qx, qy, qz
      if( math.abs( nx ) > 0.99999 ) then

         if( nx > 0 ) then

            -- Direction sur +X, rien � faire
            qw = 1
            qx = 0
            qy = 0
            qz = 0
         else

            -- Direction sur -X, rotation de 180 selon Y
            qw = 0
            qx = 0
            qy = 1
            qz = 0
         end
      else

         -- Calcul de la rotation entre id et la direction selon X
         -- L'axe de rotation est le produit vectoriel entre la direction finale et 1,0,0 : 0,-z,y
         -- L'angle de rotation est le produit scalaire : acos( nx )
         local n2 = math.sqrt( ny * ny + nz * nz )
         qw = math.cos( math.acos( nx ) / 2 )
         qx = 0
         qy = -nz/n2 * math.sin( math.acos( nx ) / 2 )
         qz =  ny/n2 * math.sin( math.acos( nx ) / 2 )

      end

      -- Passage dans le rep�re de Celestia
      local cw, cx, cy, cz
      cw, cx, cy, cz = getCelestiaQuat( qw, qx, qy, qz, localDir.objectType ) ;

      -- Nettoyage du buffer
      cleanBuffer( rt.objects[ localDir.streamId ], from )

      return cw, cx, cy, cz
   end

    -- Renvoie l'objet utilise ensuite par Celestia
    return localDir

end


--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- vts.lua
--
-- HISTORIQUE
-- VERSION : 3.5 : FA : FA_543 : 14/12/2020 : Problème Celestia avec les accents
-- VERSION : 3.4 : DM : DM_498 : 10/12/2019 : Définition des champs de vue via Azimut et Elévation
-- VERSION : 3.3 : DM : DM_332 : 07/12/2018 : Prise en charge du format OBJ pour les modèles 3D
-- VERSION : 3.0.1 : FA : FA_378 : 27/09/2016 : Changement de visibilité des ROI non prise en compte dans Celestia
-- VERSION : 3.0 : FA : FA_367 : 05/07/2016 : Camera par défaut si pas de satellite
-- VERSION : 2.6 : DM : DM_187 : 14/11/2014 : Points of interest
-- VERSION : 2.6 : DM : DM_185 : 14/11/2014 : Rosetta : mémorisiation caméra Civa
-- VERSION : 2.6 : DM : DM_184 : 14/11/2014 : Rosetta : caméra Civa
-- VERSION : 2.6 : DM : DM_176 : 14/11/2014 : Broker multi-instance
-- VERSION : 2.6 : DM : DM_55 : 14/11/2014 : DM - automatisation des tests de non-régression
-- VERSION : 2.6 : DM : DM_53 : 14/11/2014 : DM - Zones d'intérêt sur la 2DWIn
-- VERSION : 2.6 : DM : DM_37 : 14/11/2014 : DM - Simulation senseur pour demonstrateur de rendez-vous.
-- VERSION : 2.5 : DM : DM_127 : 06/03/2014 : Automatisation des prises de vue
-- VERSION : 2.5 : DM : DM_50 : 06/03/2014 : DM - Caméras manuelles
-- VERSION : 2.4 : DM : VTS 2013 (S2) : 16/12/2013 : VTS 2.4
-- VERSION : 2.3 : DM : DM_89 : 09/07/2013 : Rendre les listes de paramètres accessibles aux plugins
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Cosmographia : intégration de LUA (DM51) )
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Maintenance corrective (Celestia : Robustesse de la restauration des états si CPU chargé) )
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Maintenance corrective (Corrections mineures) )
-- VERSION : 2.2 : DM : VTS 2012 (S2) : 19/02/2013 : VTS 2.2 ( Textures dynamiques )
-- VERSION : 2.0 : DM : VTS 2012 : 23/07/2012 : VTS 2.0 ( DM 36 - Conservation du parametrage )
-- VERSION : 2.0 : DM : VTS 2012 : 23/07/2012 : VTS 2.0 ( DM 38 - Temps-reel )
-- VERSION : 2.0 : FA : FA_6 : 23/07/2012 : FA Broker - Celestia - Pas d'affichage d'orbite en pause
-- VERSION : 1.3.0 : DM : CELEST-SB-DOC-3361-CN : 27/02/2012 : VTS 1.3.0
-- VERSION : 1.3.0 : DM : CELEST-SB-DOC-3361-CN : 27/02/2012 : VTS 1.3.0 ( VTS 2011 )
-- VERSION : 1.2.2 : DM : CELEST-SB-DOC-3361-CN : 14/04/2011 : Complément VTS 2010
-- VERSION : 1.2.2 : DM : CELEST-SB-DOC-3361-CN : 14/04/2011 : Complément VTS 2010 ( Lot 2 )
-- VERSION : 1.2.2 : DM : CELEST-SB-DOC-3361-CN : 14/04/2011 : Complément VTS 2010 ( Lot 4A )
-- FIN-HISTORIQUE
-- Copyright © (2020) CNES All rights reserved
--
-- VER : $Id: vts.lua 8612 2020-12-09 20:02:41Z mmo $
--------------------------------------------------------------------------------


--------------------------------------------------------------------------------
-- Utilisation des modules externes
--------------------------------------------------------------------------------

socket = require( "socket" )
require( "vtsMath" )
require( "vtsCamera" )
require( "vtsClient" )


--------------------------------------------------------------------------------
-- initSocket
--
-- Creer un socket dans un etat initial
--------------------------------------------------------------------------------

function initSocket()

   local f
   local line

   -- Initialisation de l'objet realtime
   rt = {}

   -- Lecture du serveur Celestia et du port
   rt.servername = "localhost"
   rt.port = serverPort

   -- Initialisations diverses
   rt.time = 0.0
   rt.realTime = 0.0
   rt.lastConnectAttempt = 0.0
   rt.connected = false
   rt.paused = false
   rt.sendSync = false

   rt.client = socket.tcp()
   rt.objects = {}
   rt.streamInfos = {}
   rt.lastReceivedCamera = {}
   rt.lastMessage = ""
   rt.multi = {}
   rt.multi.sep = "|"
   rt.multi.h = 1
   rt.multi.v = 1
   
   rt.sensorCamOffset = -1 / uly2m

end


--------------------------------------------------------------------------------
-- connectToServer
--
-- Tentative de connexion au serveur de donnees
--------------------------------------------------------------------------------

function connectToServer( )

   -- Si la derniere tentative de connexion a plus de 1 secondes
   if( rt.time - rt.lastConnectAttempt > 1 ) then

      -- Memorisation de la date de la tentative
      rt.lastConnectAttempt = rt.time

      -- Tentative de connexion
      rt.client:settimeout( 1 )
      local result, errmsg = rt.client:connect( rt.servername, rt.port )
       rt.client:settimeout( 0 )

      -- Test de la connexion
      if( result == 1 ) then
         rt.connected = true
      end

      -- Affichage d'un message d'information
      if rt.connected then
         celestia:log( "Connected !" )

         -- Initialisation des corps du système solaire
         initStandardBodies()

         -- Android Experiment JEE
         -- Appel à ne pas supprimer sinon les fonctions utilisant ces objets ne marcheront plus.
         -- initCameraAndroid()

         -- Exécution du script de définition des fonctions de texture variable
         hasAltTexture = false
         textureScriptPath = celestia:getvtsextradir() .. "/VTS/vtsTexture.celx"
         local textureScriptFile = io.open( textureScriptPath, "r" )
         if textureScriptFile~=nil then
            io.close( textureScriptFile )
            dofile( textureScriptPath )
         end
         currentTex = ""

         -- Exécution du script de chargement des fichiers de Régions d'Intérêt
         local roiScriptPath = celestia:getvtsextradir() .. "/VTS/vtsInitROI.celx"
         local roiScriptFile = io.open( roiScriptPath, "r" )
         if roiScriptFile~=nil then
            io.close( roiScriptFile )
            dofile( roiScriptPath )
         end

         -- Envoi de la commande d'init
         -- celestiaAppId et realClientName sont définis dans vtsConfig.celx
         local initCommand = "INIT " .. realClientName .. " CONSTRAINT 1.0 " .. celestiaAppId .. "\n"

         local nbbytes, errorsend = rt.client:send(initCommand)
         -- Si aucun message n'a ete ecrit dans la socket
         if ( nbbytes == nil  ) then
            celestia:log( "Error connecting server : " .. errorsend )
         else
            celestia:log( "Connected to " .. rt.servername .. "(" ..
                          rt.port .. "), sent bytes: " .. nbbytes )
         end
      else
         celestia:log( "Trying to connect to " .. rt.servername .. "..." )
         celestia:log( "Cannot connect to " .. rt.servername .. "(" ..
            rt.port .. "): " .. errmsg )
      end

   end

end


--------------------------------------------------------------------------------
-- lineSplit
--
-- Séparation de la ligne de données reçues en mots avec prise en compte des
-- double quotes.
--------------------------------------------------------------------------------

function lineSplit( lineToSplit )

   local totalCount = 1
   local tab = {}
   
   -- Parcours de la ligne à splitter
   -- Le split se fait par espace, mais il faut prendre en compte les ensembles quotés
   
   -- Indique si on est en échappement ou pas
   local escaping = false
   
   -- Indique si on est dans un quote
   local quoting = false
   
   -- Contient le mot en cours
   local token = ""
   
   -- Fonction d'insertion d'un token
   insertToken = function()
      if (token ~= "") then
         tab[totalCount] = token
         totalCount = totalCount + 1
	     token = ""
      end
   end
   
   -- Parcours de la chaîne
   for i = 1, string.len(lineToSplit) do
      -- Extraction du caractère courant
      local c = lineToSplit:sub(i, i)
	  	  
	  -- Espace : le token est fini
	  if (c == ' ') then
	     if (quoting == true) then
		    token = token .. c
	     else
            insertToken(token)
	     end
		 
      -- Début ou fin de quote
	  elseif (c == '\"') then
		 if (escaping == true) then
		    -- on échappait, donc on doit insérer le caractère
			token = token .. c
	     else
		    -- on n'échappait pas
			if (quoting == true) then
			   -- fin de quote, on conserve le token et on arrête le quoting
			   quoting = false
               insertToken(token)
			else
			   -- début de quote
			   quoting = true
			end
		 end
		 
	  -- Caractère d'échappement
      elseif (c == '\\') then
	     if (quoting == true) then
	        -- Dans un quote c'est un caractère d'échappement
		    if (escaping == true) then
		       -- on echappait déjà, donc on doit insérer le caractère
		  	   token = token .. c
	        else
		       -- on n'échappait pas encore
		  	   escaping = true
	        end
		 else
		    -- en dehors d'un quote c'est un caractère normal
			token = token .. c
		 end
		 
	  -- Tout autre caractère
	  else
	     -- Cas général : ni espace ni guillement
	     token = token .. c
	  end
   end
   
   -- Il peut rester un token à insérer
   insertToken(token)
   
   return tab
end


--------------------------------------------------------------------------------
-- tick
--
-- Fonction appelee par Celestia a chaque avance dans le temps (rafraichissement
-- complet de l'affichage).
--
-- dt : temps en seconde ecoule depuis le dernier appel
--------------------------------------------------------------------------------

hooks = {}
function hooks:tick( dt )

   -- Avance du temps local
   rt.ptime = rt.time
   rt.time = rt.time + dt

   -- Verification de la connexion
   if not rt.connected then

      -- Mise en pause de Celestia
      celestia:settimescale( 0 )
      
      -- Caméra par défaut pendant le chargement + message
      if( rt.firstConnectionAttempt == nil ) then
         rt.firstConnectionAttempt = true
         setCameraSynchronous( "Sol", "Sol", "+X", 0.33 )
         celestia:log( "Loading..." ) 
      end         

      -- Connexion
      connectToServer()

   else

      -- We're already connected; attempt to read from the socket. We've set
      -- the timeout to a very low value, so the read is effectively non-
      -- blocking.
      -- TODO: this is not very efficient; investigate using select() instead

      -- Depuis la réception de la demande de synchronisation, on a fait une boucle d'affichage
      if( rt.sendSync == true ) then
         rt.client:send( "CMD SERVICE Synchronized\n" )
         rt.sendSync = false
      end
      
      -- Remise à zéro des dimensions mémorisées
      rt.width = 0

      -- Lecture du premier message
      local msg, err, partial = rt.client:receive('*l')

      -- Si un message a ete lu dans la socket
      while msg ~= nil and #msg > 0 do

         -- Split the record received from the socket into separate words
         rt.values = lineSplit( msg )
         rt.lastMessage = msg

         -- Test du type de paquet recu
         if ( rt.values[1] == "DATA" ) then

            -- Memorisation de l'heure de la reception
            rt.pRealTime = rt.realTime
            rt.realTime = rt.time

            -- Traitement de la reception d'un paquet de donnees
            receiveData()

         -- Teste la reception d'une commande
         elseif ( rt.values[1] == "TIME" ) then

            -- Traitement de la reception d'un paquet temps
            receiveTime()

         -- Teste la reception d'une commande
         elseif ( rt.values[1] == "CMD" ) then

            -- Traitement de la reception d'un paquet de commande
            local continueLoop = receiveCmd()
            if( continueLoop == false ) then
               break
            end

         -- Teste la reception d'un paquet inconnu
         else
            celestia:log( "Unknown protocol command received" )
         end

        -- Lecture du message suivant
        msg, err, partial = rt.client:receive('*l')

      end

   end

   -- Android Experiment JEE
   -- if( boolHookCodeAndroid == true ) then
   --    hookTestCodeAndroid( dt )
   -- end

   return false

end


--------------------------------------------------------------------------------
-- sendimage
--
-- Fonction appelée spécifiquement pour VTS quand Cosmographia fait un rendu 
-- sans overlay de la scène et pousse l'image vers lua de manière asynchrone
-- après un appel à celestia:capturebuffertostring( "image" )
--
-- str : la chaîne représentant l'image en base64 à envoyer
--------------------------------------------------------------------------------

function hooks:sendimage( str )

   -- Construction de la commande
   local cmdToSend = "DATA 0 image " .. str .. "\n"

   -- Timeout de 10 secondes pour le transfert
   rt.client:settimeout( 10, 'b' )
   local b,err = rt.client:send( cmdToSend )
   rt.client:settimeout( 0, 'b' )
end


--------------------------------------------------------------------------------
-- Enregistrement de l'objet contenant les fonctions crochets
--------------------------------------------------------------------------------

celestia:setluahook( hooks )


--------------------------------------------------------------------------------
-- Pas de buffering pour les output (mieux pour le debug)
--------------------------------------------------------------------------------

io.stdout:setvbuf( "no" )


--------------------------------------------------------------------------------
-- On force la locale Ã  C pour utiliser le point comme sÃ©parateur dÃ©cimal
--------------------------------------------------------------------------------

os.setlocale( 'C', 'numeric' )


--------------------------------------------------------------------------------
-- Initialisations
--------------------------------------------------------------------------------

-- Exécution du script de config généré par le launcher Celestia pour positionner certaines variables dynamiques
dofile(celestia:getvtsextradir() .. "/VTS/vtsConfig.celx")

initSocket()
initSimulation()



--------------------------------- End Of File ----------------------------------

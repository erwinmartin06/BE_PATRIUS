@rem ---------------------------------------------------------------------------
@rem cleanerCelestia.bat
@rem
@rem HISTORIQUE
@rem VERSION : 3.5 : DM : VTS 2020 (S2) : 14/12/2020 : VTS 3.5
@rem VERSION : 2.6 : DM : DM_177 : 14/11/2014 : Nettoyage des caches
@rem FIN-HISTORIQUE
@rem Copyright Â© (2020) CNES All rights reserved
@rem
@rem Suppression des répertoires temporaires de VTS
@rem ---------------------------------------------------------------------------


@echo off 

for %%p in (%*) do (
   @rem Suppression des répertoires temporaires
   if "%%p"=="--clear" (
      for /d %%a in (%~dp0/extras_*) do rd /s /q "%%a"

      @rem Code retour
      exit /B 0
   )
)

@rem End of file

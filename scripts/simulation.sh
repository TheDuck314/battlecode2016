#!/bin/bash

teams=""
teams+="sprint_11 "
teams+="felix15 "
teams+="ttm_dense_pure_11 "
teams+="ttm_dense_9 "
teams+="ttm_heal "
teams+="focused16 "
teams+="sodier "
teams+="examplefuncsplayer "
teams+="donothing "
teams+="seeding_base18 "
teams+="soldier_turret_retreat17 "
teams+="soldier_into_turret16 "
teams+="scout_viper14 "
teams+="postsprint_13 "
teams+="more_soldier_into_turret16 "
teams+="supercowpowers_archonmove8 "
teams+="supercowpowers7 "
# teams+="team028 "
# teams+="explore "
# teams+="first "
# teams+="guard "
# teams+="radar8 "
# teams+="radar_archonS10 "
# teams+="radar_channel10 "
# teams+="radar_follow9 "
# teams+="radar_limitscouts10 "
# teams+="radar_pair10 "
# teams+="radar_pair_turretmemory10 "
# teams+="radar_srp9 "
# teams+="second "
# teams+="sodier_rubble "
# teams+="sprint_base_11 "
# teams+="supercowpowers7_noheal "
# teams+="ttm_explore "
# teams+="ttm_scout "
# teams+="ttm_scout_move "
# teams+="turret "
# teams+="turret_scout "

simulate() {
    local team_a="$1"
    local team_b="$2"
    local logtmp="logs/tmplog"
    local log="logs/log--$team_a--$team_b"
    if [ -f "$log" ] ; then
        echo "$log" exist
    else
        echo ---------------------------------------------------------------
        echo ---------------------------------------------------------------
        echo ---------------------------------------------------------------
        echo simulation $team_a vs $team_b
        echo ---------------------------------------------------------------
        echo ---------------------------------------------------------------
        echo ---------------------------------------------------------------
        cat scripts/bc.conf.template | sed "s/TEAM-A/$team_a/" | sed "s/TEAM-B/$team_b/" >bc.conf
        time ant headless |& tee "$logtmp"
        mv "$logtmp" "$log"
    fi
}

mkdir logs
echo $teams

if [ "$#" == 2 ] ; then
    simulate "$1" "$2"
    simulate "$2" "$1"
    exit
fi

if [ "$#" == 1 ] ; then
    for team_b in $teams ; do
        simulate "$1" "$team_b"
        simulate "$team_b" "$1"
    done
    exit
fi

for team_a in $teams ; do
    for team_b in $teams ; do
        simulate "$team_a" "$team_b"
        simulate "$team_b" "$team_a"
    done
done

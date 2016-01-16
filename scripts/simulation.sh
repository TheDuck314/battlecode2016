#!/bin/bash

teams=""
teams+="donothing "
teams+="examplefuncsplayer "
teams+="sprint_11 "
teams+="postsprint_13 "
teams+="scout_viper14 "
teams+="felix15 "
teams+="ttm_dense_9 "
teams+="ttm_dense_pure_11 "
teams+="ttm_heal "
teams+="sodier "
teams+="supercowpowers7 "
teams+="supercowpowers_archonmove8 "
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
        ant headless |& tee "$logtmp"
        mv "$logtmp" "$log"
    fi
}

mkdir logs
echo $teams
for team_a in $teams ; do
    for team_b in $teams ; do
        simulate "$team_a" "$team_b"
        simulate "$team_b" "$team_a"
    done
done

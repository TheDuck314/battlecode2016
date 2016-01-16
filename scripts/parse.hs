#!/usr/bin/runghc

module Main where

import Control.Monad
import Control.Arrow
import Data.Function
import Data.List
import System.Directory
import System.IO
import System.FilePath
import Text.Printf

splitWith :: String -> String -> [String]
splitWith sep string = go id string where
    n = length sep
    go f "" = [f ""]
    go f str
        | sep `isPrefixOf` str = f "" : go id (drop n str)
        | otherwise = go (f . (head str :)) (tail str)

parseLogname :: String -> (String, String)
parseLogname logname = (teamA, teamB) where
    [_, teamA, teamB] = splitWith "--" logname

analysisLog :: String -> (Int, Int)
analysisLog fileContent = (awins, bwins) where
    awins = length $ filter ("(A)" `elem`) $ wslines
    bwins = length $ filter ("(B)" `elem`) $ wslines
    wslines = filter (\ws -> not (null $ drop 2 ws) && "wins" `elem` ws) . map words $ lines fileContent

runAnalysis :: FilePath -> IO ((String, String), (Int, Int))
runAnalysis logname = do
    wins <- readFile logname >>= return . analysisLog
    return (teams, wins)
  where
    teams = parseLogname logname

showAnalysis :: ((String, String), (Int, Int)) -> String
showAnalysis ((teamA, teamB), (awins, bwins)) = printf "%30s %30s %10d %10d" teamA teamB awins bwins

sortWith :: (Ord b) => (a -> b) -> [a] -> [a]
sortWith f = sortBy (compare `on` f)

groupWith :: (Eq b) => (a -> b) -> [a] -> [[a]]
groupWith f = groupBy ((==) `on` f)

runAllAnalyses :: IO ()
runAllAnalyses = do
    logs <- liftM (filter ("logs/log--" `isPrefixOf`) . map ("logs" </>)) $ getDirectoryContents "logs"
    vs' <- mapM runAnalysis logs
    let abvs' = map ((\(a,b) -> (min a b, max a b)) . fst &&& id) vs'
        abvs = sortWith (fst &&& snd . snd . snd) abvs'
        gvs = map (map snd) $ groupWith fst abvs
    putStrLn $ printf "# %28s %30s %10s %10s" "teamA" "teamB" "Awins" "Bwins"
    putStrLn ""
    mapM_ putStrLn $ map (unlines . map showAnalysis) gvs

main :: IO ()
main = do
    runAllAnalyses

package
{


import flash.geom.Point;


/** The Controller class holds game logic, and updates game state in the model. */
    
public class Controller 
{
    // OUBLIC CONSTANTS
    public static const MIN_WORD_LENGTH : int = 3;

    
    // PUBLIC METHODS
    
    public function Controller (model : Model, rounds : RoundProvider) : void
    {
        _model = model;
        _rounds = rounds;
        _rounds.addEventListener (RoundProvider.ROUND_STARTED_STATE, roundStartedHandler);
        _rounds.addEventListener (RoundProvider.ROUND_ENDED_STATE, roundEndedHandler);
    }

    public function setModel (model : Model) : void
    {
        _model = model;
    }

    /** Returns true if the controller should accept player inputs, false otherwise */
    public function get enabled () : Boolean
    {
        return _enabled;
    }

    /** Sets the value specifying whether the controller should accept player inputs */
    public function set enabled (value : Boolean) : void
    {
        _enabled = value;
    }
    
    /** Takes a new letter from the UI, and checks it against game logic. */
    public function tryAddLetter (position : Point) : void
    {
        if (enabled)
        {
            // Position of the letter on top of the stack 
            var lastLetterPosition : Point = _model.getLastLetterPosition ();
            
            // Did the player click on the first letter? If so, just add it.
            var noPreviousLetterFound : Boolean = (lastLetterPosition == null);
            if (noPreviousLetterFound)
            {
                _model.selectLetterAtPosition (position);
                return;
            }
            
            // Did the player click on the last letter they added? If so, remove it.
            if (position.equals (lastLetterPosition))
            {
                _model.removeLastSelectedLetter ();
                return;
            }
            
            // Did the player click on an empty letter next to the last selected one?
            // If so, add it.
            var isValidNeighbor : Boolean = (areNeighbors (position, lastLetterPosition) &&
                                             ! _model.isLetterSelectedAtPosition (position));
            if (isValidNeighbor)
            {
                _model.selectLetterAtPosition (position);
                return;
            }
            
            // Player clicked on an invalid position - don't do anything
        }
    }


    /** Signals that the currently selected word is a candidate for scoring.
        It will be matched against the dictionary, and added to the model. */
    public function tryScoreWord (word : String) : void
    {
        // First, check to make sure it's of the correct length (in characters)
        if (word.length < MIN_WORD_LENGTH) return;

        // Now check if it's an actual word
        if (!DictionaryService.checkWord (Properties.LOCALE, word)) return;

        // Find the word score
        var score : Number = word.length;
        
        // Finally, process the new word. Notice that we don't check if it's already
        // been claimed - the model will take care of that, because there's a network
        // round-trip involved, and therefore potential of contention.
        _model.addScore (word, score);
    }
            


    // PRIVATE EVENT HANDLERS

    /** Called when the round starts - enables user input, randomizes data. */
    private function roundStartedHandler (newState : String) : void
    {
        initializeLetterSet ();
        enabled = true;
    }

    /** Called when the round ends - disables user input. */
    private function roundEndedHandler (newState : String) : void
    {
        enabled = false;
    }



    // PRIVATE METHODS

    /** Determines whether the given /position/ is a neighbor of specified /original/
        position (defined as being one square away from each other). */
    private function areNeighbors (position : Point, origin : Point) : Boolean
    {
        return (! position.equals (origin) &&
                Math.abs (position.x - origin.x) <= 1 &&
                Math.abs (position.y - origin.y) <= 1);
    }
    
    /** Initializes a new letter set. */
    private function initializeLetterSet () : void
    {
        // Get a set of letters
        var s : Array = DictionaryService.getLetterSet (Properties.LOCALE,
                                                        Properties.LETTER_COUNT);
        
        Assert.True (s.length == Properties.LETTER_COUNT,
                     "DictionaryService returned an invalid letter set.");

        _model.sendNewLetterSet (s);
    }


    
    // PRIVATE VARIABLES

    /** Game data interface */
    private var _model : Model;

    /** Round provider */
    private var _rounds : RoundProvider;

    /** Does the controller accept user input? */
    private var _enabled : Boolean;
}

}


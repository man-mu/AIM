import {useNavigate} from "react-router";

function Login() {
    const navigate = useNavigate();
    return (
        <div>
            <div><input type="text" placeholder="Username" /></div>
            <div><input type="password" placeholder="Password" /></div>
            <div><button
                style={{backgroundColor: 'red', cursor: 'pointer'}}
                onClick={() => {
                    navigate('/');
                    console.log('login success')
                }}
            >Login</button></div>
        </div>
    )
}
export default Login;